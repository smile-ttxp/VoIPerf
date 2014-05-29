#!/usr/bin/env python

# vim: columns=100
# vim: expandtab softtabstop=4 shiftwidth=4 tabstop=4

import logging
import os
import select
import socket
import threading
import time
import zlib

# Local imports
import Config
import MeasurementUtils as MUtils
import NetUtils
import PacketFooter
import Trace

class RandomTraceSender:

    max_packet_size = Config.TRACE_MAX_PACKET_SIZE
    wait_timeout = Config.TRACE_WAIT_TIMEOUT
    recv_timeout = Config.TRACE_RECV_TIMEOUT
    end_message = Config.TRACE_END_MESSAGE
    punch_packet = Config.HOLE_PUNCHING_PACKET
    punch_packet_timeout = Config.HOLE_PUNCHING_TIMEOUT

    def __init__(self, control_connection_fd,
                 server_address, client_address, output_dir):

        self.logger = logging.getLogger('RandomTraceSender (%s:%s)' % client_address)
        self.logger.debug('init')

        self.control_connection_fd = control_connection_fd

        self.listen_IP = server_address[0]
        self.client_IP = client_address[0]

        self.output_dir = output_dir

    def recv_replies(self, trace_connection, replies):

        self.logger.debug('receive replies thread started')
        trace_connection.settimeout(RandomTraceSender.wait_timeout)
        self.logger.debug('trace connection replies wait timeout set to %s seconds'
                          % RandomTraceSender.wait_timeout)
        
        try:
            first = True
            while True:
                packet = trace_connection.recv(RandomTraceSender.max_packet_size)
                timestamp = time.time()
                #self.logger.debug('reply received size=%s' % len(packet)) 
                if packet == RandomTraceSender.punch_packet:
                    self.logger.info('duplicate hole punching packet received, ignoring it')
                    continue
                if first:
                    trace_connection.settimeout(RandomTraceSender.recv_timeout)
                    self.logger.info('first packet received')
                    self.logger.debug('trace connection recv timeout set to %s seconds'
                                      % RandomTraceSender.recv_timeout)
                    first = False
                footer = PacketFooter.load(packet)
                if footer != None:
                    replies.append((footer.seq, timestamp))
                else:
                    self.logger.warning('BUG: received a packet with no footer!')
            return replies 
        except socket.timeout:
            self.logger.info('trace connection timeout, assuming measurement has finished')

        self.logger.debug('receiver thread has finished')

    def receive_punch_packet(self, trace_connection):
        self.logger.debug('waiting for udp hole punching packet for %s seconds'
                          % RandomTraceSender.punch_packet_timeout)
        while True:
            ready = select.select([trace_connection], [], [],
                                  RandomTraceSender.punch_packet_timeout)
            if ready[0]:
                packet, address = trace_connection.recvfrom(RandomTraceSender.max_packet_size)
                if address[0] != self.client_IP:
                    self.logger.warning('ignoring packet from unexpected address %s:%s' % address)
                    continue
                if packet == RandomTraceSender.punch_packet:
                    self.logger.debug('hole punching packet received')
                    return address
                else:
                    raise ValueError('BUG: invalid punch packet payload received!')
            else:
                self.logger.error('hole punching packet not received on time, giving up')
                raise socket.timeout('hole punching packet not received')

    def send_packets(self, trace_connection, trace, sent_packets):

        self.logger.debug('send trace thread started')

        _, trace_packets = trace

        seq = -1
        prev_packet_time = None
        for packet in trace_packets:

            seq += 1

            packet_delta = 0
            if prev_packet_time != None:
                packet_delta = packet['timestamp'] - prev_packet_time
            prev_packet_time = packet['timestamp']

            if packet_delta > 0:
                MUtils.busywait_sleep(packet_delta)

            timestamp = time.time()
            payload = PacketFooter.write(packet['payload'], seq, int(timestamp * 1000))
            sent_packets[seq] = seq, timestamp, payload

            trace_connection.sendall(payload)
            #self.logger.debug('packet sent size=%s' % len(packet['payload'])) 

        trace_connection.sendall(RandomTraceSender.end_message)

        self.logger.debug('sender thread has finished')

    def compute_trace_statistics(self, sent_packets, recv_seq, recv_packets):

        trace_size = len(sent_packets.keys())
        nreceived_packets = len(set(recv_seq))

        stats = {}
        stats[Config.PACKET_LOSS_STAT] = 1.0 - (nreceived_packets / max(1.0, float(trace_size)))

        if recv_packets:
            tot_bytes = sum([packet['length'] for packet in recv_packets])
            start = recv_packets[0]['timestamp']
            end = recv_packets[-1]['timestamp']
            duration = end - start
            stats[Config.AVG_RATE_STAT] = (tot_bytes * 8) / (1024.0 * max(1.0, duration))
        else:
            stats[Config.AVG_RATE_STAT] = 0.0

        return stats

    def load_trace(self, trace_name, trace_id):
        filepath = os.path.join(Config.TRACES_DIR, trace_name)
        self.logger.debug('reading trace file %s' % filepath)
        try:
            with open(filepath) as f:
                trace = Trace.read_trace(f)
                if trace[0]['id'] != trace_id:
                    raise ValueError('trace id mismatch (%s instead of %s) for trace %s'
                                     % (trace_id, trace[0]['id'], trace_name))
                return trace
        except (IOError, ValueError) as e:
            self.logger.error('failed to read trace file %s (%s)' % (filepath, e))
            raise

    def get_trace_RTTs(self, replies, sent_packets):
        RTTs = []
        for seq, recv_ts in replies:
            try:
                _, sent_ts, _ = sent_packets[seq]
                RTTs.append(1000 * (recv_ts - sent_ts))
            except KeyError:
                RTTs.append(-1)
        return RTTs

    def get_sent_timestamps(self, sent_packets):
        return [1000 * ts for _, ts, _ in sorted(sent_packets.values(), key = lambda (x, y, z): x)]

    def run(self):

        timestamp = time.time()
        trace_filepath = os.path.join(self.output_dir, '%s.trace' % int(timestamp))
        pcap_filepath = os.path.join(self.output_dir, '%s.pcap' % int(timestamp))

        try:
            trace_name = self.control_connection_fd.readline()
            trace_id = self.control_connection_fd.readline()
            if not (trace_name and trace_id):
                raise socket.error('connection closed by remote host')
            trace_id = int(trace_id)
            trace_name = trace_name.strip()
            trace = self.load_trace(trace_name, trace_id)
            self.logger.info('trace name=%s trace id=%s pcap=%s' %
                             (trace_name, trace_id, pcap_filepath))
        except (ValueError, IOError, socket.error, socket.timeout) as e:
            self.logger.error('trace setup failed (%s)' % e)
            raise

        trace_connection = None
        try:
            self.logger.info('opening trace connection')
            trace_connection = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            trace_connection.bind((self.listen_IP, 0)) # use a random port
            listen_address = trace_connection.getsockname()

            # Create the tcpdump object first and sleep one second,
            # so we can sure all the packets get sniffed
            self.logger.info('opening capture file %s' % pcap_filepath)
            tcpdump = NetUtils.Tcpdump(Config.CAPTURE_INTERFACE, self.client_IP,
                                       listen_address[1], pcap_filepath)
            tcpdump.make_thread().start()
            time.sleep(1)

            # Tell the client what port should be used for the measurement
            self.control_connection_fd.write(str(listen_address[0]) + '\n')
            self.control_connection_fd.write(str(listen_address[1]) + '\n')
            self.control_connection_fd.flush()

            # Receive a first packet to punch the client-side NAT and set
            # the trace destination according to the packet's source address
            client_side_address = self.receive_punch_packet(trace_connection)
            trace_connection.connect(client_side_address)

            # Start one thread that receives the replies 
            replies = []
            receiver = threading.Thread(target = self.recv_replies,
                                        args = (trace_connection, replies))
            receiver.start()

            # Start another thread that sends the trace packets
            sent_packets = {}
            sender = threading.Thread(target = self.send_packets,
                                      args = (trace_connection, trace, sent_packets))
            sender.start()

            # Wait for the threads to complete and close the connection
            receiver.join()
            sender.join()
            self.logger.info('closing trace connection')
            trace_connection.close()
            tcpdump.kill()

            self.logger.debug('saving server side measurement info')
            server_side_info = {}
            server_side_info['trace_rtts'] = self.get_trace_RTTs(replies, sent_packets)
            server_side_info['sent_timestamps'] = self.get_sent_timestamps(sent_packets)

            self.logger.info('reading measurement info from client')
            client_side_info = MUtils.read_compressed_json(self.control_connection_fd)
            try:
                if len(client_side_info['recv_timestamps']) != len(client_side_info['recv_seq']):
                    raise ValueError('len(recv_timestamps) != len(recv_seq)')
            except (KeyError, ValueError) as e:
                self.logger.error('BUG client side info is not valid (%s)' % e)
                raise ValueError('invalid client side info')

            # Use the data sent from the client to reconstruct the trace it received
            recv_packets = self.rebuild_received_trace_packets(client_side_info, sent_packets)

            self.logger.info('sending trace statistics')
            trace_statistics = self.compute_trace_statistics(sent_packets,
                                                             client_side_info['recv_seq'],
                                                             recv_packets)
            MUtils.send_json(self.control_connection_fd, trace_statistics)

            result = {}
            result['timestamp'] = timestamp 
            result['trace_id'] = trace_id
            result['client_side_info'] = client_side_info
            result['server_side_info'] = server_side_info
            result['statistics'] = trace_statistics
            result['server_address'] = {'IP': listen_address[0],
                                        'port': listen_address[1]}
            result['client_address'] = {'IP': client_side_address[0],
                                        'port': client_side_address[1]}
            result['trace_filename'] = os.path.basename(trace_filepath)
            result['pcap_filename'] = os.path.basename(pcap_filepath)

            # Save the resulting trace
            self.save_sent_trace(recv_packets, result, trace_filepath)

            return result
        except (ValueError, socket.error, socket.timeout, zlib.error) as e:
            self.logger.error('read trace failed (%s)' % e)
            raise
        finally:
            # To be sure that the trace connection
            # gets closed even in case of errors
            if trace_connection: trace_connection.close()

    def rebuild_received_trace_packets(self, client_side_info, sent_packets):
        self.logger.debug('rebuilding the trace received by the client')

        recv_packets = []
        for i, recv_ts in enumerate(client_side_info['recv_timestamps']):
            try:
                seq = client_side_info['recv_seq'][i]
                _, _, payload = sent_packets[seq]
            except KeyError:
                self.logger.error('BUG: client received a packet with invalid sequence number?')
                continue

            packet = {}
            packet['timestamp'] = recv_ts / 1000.0
            packet['length'] = len(payload)
            packet['payload'] = payload
            recv_packets.append(packet)
        return recv_packets

    def save_sent_trace(self, recv_packets, result, trace_filepath):
        try:
            self.logger.info('saving client received trace to %s' % trace_filepath)
            trace_file = open(trace_filepath, 'w')

            preamble = {}
            preamble['id'] = result['trace_id']
            preamble['dport'] = result['client_address']['port']

            Trace.write_v2_trace((preamble, recv_packets), trace_file)
            
        except (IOError, ValueError) as e:
            self.logger.error('failed to write trace file %s (%s)' % (trace_filepath, e))
        finally:
            if trace_file: trace_file.close()
