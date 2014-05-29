#!/usr/bin/env python

# vim: columns=100
# vim: expandtab softtabstop=4 shiftwidth=4 tabstop=4

import logging
import os
import socket
import time
import zlib

# Local imports
import Config
import MeasurementUtils as MUtils
import NetUtils
import RTPHeader
import Trace

class RTPTraceReceiver:

    max_packet_size = Config.TRACE_MAX_PACKET_SIZE
    send_bsize = Config.TRACE_SEND_BSIZE
    recv_bsize = Config.TRACE_RECV_BSIZE
    wait_timeout = Config.TRACE_WAIT_TIMEOUT
    recv_timeout = Config.TRACE_RECV_TIMEOUT
    end_message = Config.TRACE_END_MESSAGE

    def __init__(self, control_connection_fd,
                 server_address, client_address, output_dir):

        self.logger = logging.getLogger('RTPTraceReceiver (%s:%s)' % client_address)
        self.logger.debug('init')

        self.control_connection_fd = control_connection_fd

        self.listen_IP = server_address[0]

        self.client_IP = client_address[0]
        self.output_dir = output_dir

    def receive_packets(self, trace_connection):

        # Set a large receive buffer size so as to be sure we don't lose any packet
        trace_connection.setsockopt(socket.SOL_SOCKET,
                                    socket.SO_RCVBUF,
                                    RTPTraceReceiver.recv_bsize)
        bsize = trace_connection.getsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF)
        self.logger.debug('trace connection recv buffer size set to %s bytes' % bsize)

        # Set a large send buffer size so as to minimize the
        # probability of getting stuck when sending the replies
        # to the client
        trace_connection.setsockopt(socket.SOL_SOCKET,
                                    socket.SO_SNDBUF,
                                    RTPTraceReceiver.send_bsize)
        bsize = trace_connection.getsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF)
        self.logger.debug('trace connection send buffer size set to %s bytes' % bsize)

        trace_connection.settimeout(RTPTraceReceiver.wait_timeout)
        self.logger.debug('trace connection wait timeout set to %s seconds'
                          % RTPTraceReceiver.wait_timeout)
        
        src_port = None
        trace = []
        try:
            first = True
            while True:
                # Receive the next packet and check its address
                packet, address = trace_connection.recvfrom(RTPTraceReceiver.max_packet_size)
                timestamp = time.time()
                if address[0] != self.client_IP:
                    self.logger.warning('ignoring packet from unexpected address %s:%s' % address)
                    continue
                if src_port != None and address[1] != src_port:
                    self.logger.error('client\'s source port changed from %s to %s!' %
                                      (src_port, address[1]))
                    raise IOError('client source port changed during measurement')
                src_port = address[1]
                #self.logger.debug('packet received size=%s' % len(packet)) 

                # Send back a reply
                if first:
                    trace_connection.settimeout(RTPTraceReceiver.recv_timeout)
                    self.logger.info('first packet received')
                    self.logger.debug('trace connection recv timeout set to %s seconds'
                                      % RTPTraceReceiver.recv_timeout)
                    first = False
                if packet == RTPTraceReceiver.end_message:
                    self.logger.info('end trace received')
                    break
                if RTPHeader.SIZE <= len(packet):
                    header = packet[:RTPHeader.SIZE]
                    trace_connection.sendto(header, address)
                    trace.append((timestamp, packet, header))
                else:
                    self.logger.error('BUG: received a packet without RTP header!')
            return src_port, trace
        except socket.timeout:
            self.logger.warning('trace connection timeout')
            raise
        finally:
            self.logger.info('closing trace connection')

    def compute_trace_statistics(self, trace, trace_size):

        nreceived_packets = len(set([header for _, _, header in trace]))

        stats = {}
        stats[Config.PACKET_LOSS_STAT] = 1.0 - (nreceived_packets / max(1.0, float(trace_size)))

        if trace:
            tot_bytes = sum([len(packet) for _, packet, _ in trace])
            start, _, _ = trace[0]
            end, _, _ = trace[-1]
            duration = end - start
            stats[Config.AVG_RATE_STAT] = (tot_bytes * 8) / (1024.0 * max(1.0, duration))
        else:
            stats[Config.AVG_RATE_STAT] = 0.0

        return stats

    def run(self):

        timestamp = time.time()
        trace_filepath = os.path.join(self.output_dir, '%s.trace' % int(timestamp))
        pcap_filepath = os.path.join(self.output_dir, '%s.pcap' % int(timestamp))

        try:
            trace_id = self.control_connection_fd.readline()
            trace_size = self.control_connection_fd.readline()
            if not (trace_id and trace_size):
                raise socket.error('connection closed by remote host')
            trace_id = int(trace_id)
            trace_size = int(trace_size)
            self.logger.info('trace id=%s size=%s file=%s pcap=%s' %
                             (trace_id, trace_size, trace_filepath, pcap_filepath))
        except (ValueError, socket.error, socket.timeout) as e:
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

            self.control_connection_fd.write(str(listen_address[0]) + '\n')
            self.control_connection_fd.write(str(listen_address[1]) + '\n')
            self.control_connection_fd.flush()

            # Receive the trace packets
            client_port, trace = self.receive_packets(trace_connection)
            trace_connection.close()
            tcpdump.kill()

            self.logger.info('reading measurement info from client')
            client_side_info = MUtils.read_compressed_json(self.control_connection_fd)

            self.logger.info('sending trace statistics')
            trace_statistics = self.compute_trace_statistics(trace, trace_size)
            MUtils.send_json(self.control_connection_fd, trace_statistics)

            result = {}
            result['timestamp'] = timestamp 
            result['trace_id'] = trace_id
            result['trace_size'] = trace_size
            result['client_side_info'] = client_side_info
            result['statistics'] = trace_statistics
            result['server_address'] = {'IP': listen_address[0], 'port': listen_address[1]}
            result['client_address'] = {'IP': self.client_IP, 'port': client_port}
            result['trace_filename'] = os.path.basename(trace_filepath)
            result['pcap_filename'] = os.path.basename(pcap_filepath)

            self.save_received_trace(trace, result, trace_filepath)

            return result
        except (ValueError, socket.error, socket.timeout, zlib.error) as e:
            self.logger.error('read trace failed (%s)' % e)
            raise
        finally:
            # To be sure that the trace connection
            # gets closed even in case of errors
            if trace_connection: trace_connection.close()

    def save_received_trace(self, trace, result, trace_filepath):
        try:
            self.logger.info('writing trace file %s' % trace_filepath)
            trace_file = open(trace_filepath, 'w')

            preamble = {}
            preamble['id'] = result['trace_id']
            preamble['dport'] = result['server_address']['port']

            packets = []
            for timestamp, payload, _ in trace:
                packets.append({'timestamp': timestamp, 'length': len(payload),
                                'payload': payload})

            Trace.write_rtp_trace((preamble, packets), trace_file)
            
        except (IOError, ValueError) as e:
            self.logger.error('failed to write trace file %s (%s)' % (trace_filepath, e))
        finally:
            if trace_file: trace_file.close()
