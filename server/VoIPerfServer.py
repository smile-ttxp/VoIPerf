#!/usr/bin/env python

# vim: columns=100
# vim: expandtab softtabstop=4 shiftwidth=4 tabstop=4

# Inspired by: http://bip.weizmann.ac.il/course/python/PyMOTW/PyMOTW/docs/SocketServer/index.html

import datetime
import json
import logging
import os
import socket
import SocketServer
import threading
import time
import traceback
import zlib

# Local imports
import Config
import MeasurementUtils as MUtils
import NetUtils
import RandomTraceReceiver
import RandomTraceSender
import RTPTraceReceiver
import RTPTraceSender

class MeasurementHandler(SocketServer.BaseRequestHandler):

    idle_timeout = Config.DEFAULT_CLIENT_IDLE_TIMEOUT

    def __init__(self, request, client_address, voiperf_server):
        self.logger = logging.getLogger('MeasurementHandler (%s:%s)' % client_address)
        self.logger.info('client connected')
        SocketServer.BaseRequestHandler.__init__(self, request, client_address, voiperf_server)

    def setup(self):
        self.start_time = time.time()
        self.request.settimeout(MeasurementHandler.idle_timeout)
        self.logger.info('client socket idle timeout set to %s seconds',
                         MeasurementHandler.idle_timeout)
        self.fd = self.request.makefile()

    def create_measurement_dir(self, output_dir, client_info, start_time):
        d = datetime.datetime.fromtimestamp(start_time)
        measurement_dir = os.path.join(output_dir, client_info['device_info']['unique_id'],
                                       str(d.year), str(d.month), str(d.day),
                                       '%02d:%02d.%02d' % (d.hour, d.minute, d.second))
        try:
            if not os.path.isdir(measurement_dir):
                os.makedirs(measurement_dir)
        except OSError as e:
            raise OSError('failed to create measurement dir %s: %s' % (measurement_dir, e))
        return measurement_dir

    def read_client_info(self):
        client_info = MUtils.read_json(self.fd)
        if 'device_info' not in client_info or\
           'phone_status' not in client_info or\
           'network_status' not in client_info:
            raise ValueError('failed to read client info: incomplete client info received')
        if 'unique_id' not in client_info['device_info']:
            raise ValueError('failed to read client info: client id not found')
        return client_info

    def handle(self):
        if not self.server.can_start_measurement():
            self.logger.info('refusing connection, server is busy')
            self.fd.write(Config.MEASUREMENT_SERVER_BUSY_MSG + '\n')
            self.fd.flush()
            return
        try:
            self.fd.write(Config.MEASUREMENT_SERVER_AVAILABLE_MSG + '\n')
            self.fd.flush()

            self.client_info = self.read_client_info()
            self.logger.info('client id is %s' % self.client_info['device_info']['unique_id'])

            self.measurement_dir = self.create_measurement_dir(self.server.output_dir,
                                                               self.client_info,
                                                               self.start_time)
            self.logger.info('measurement results directory is %s' % self.measurement_dir)

            self.logger.info('running traceroute')
            self.traceroute = NetUtils.TraceRoute(self.client_address[0])
            self.traceroute.run()

            self.logger.info('receiving traceroute from client')
            self.remote_traceroute = MUtils.read_compressed_json(self.fd)

            self.logger.info('receiving traces')
            self.received_traces = []
            while True:
                mtype = self.fd.readline().strip()
                if not mtype:
                    raise socket.error('connection closed by remote host')
                elif mtype == Config.END_MEASUREMENTS:
                    self.logger.info('END_MEASUREMENTS received')
                    break
                elif mtype == Config.RTP_SEND_MEASUREMENT_TYPE:
                    handler = RTPTraceReceiver.RTPTraceReceiver(self.fd,
                                                                 self.request.getsockname(),
                                                                 self.client_address,
                                                                 self.measurement_dir)
                elif mtype == Config.RTP_RECV_MEASUREMENT_TYPE:
                    handler = RTPTraceSender.RTPTraceSender(self.fd,
                                                            self.request.getsockname(),
                                                            self.client_address,
                                                            self.measurement_dir)
                elif mtype == Config.RANDOM_SEND_MEASUREMENT_TYPE:
                    handler = RandomTraceReceiver.RandomTraceReceiver(self.fd,
                                                                      self.request.getsockname(),
                                                                      self.client_address,
                                                                      self.measurement_dir)
                elif mtype == Config.RANDOM_RECV_MEASUREMENT_TYPE:
                    handler = RandomTraceSender.RandomTraceSender(self.fd,
                                                                  self.request.getsockname(),
                                                                  self.client_address,
                                                                  self.measurement_dir)
                else:
                    raise ValueError('BUG: received unknown measurement type %s' % mtype)

                self.logger.info('starting measurement type %s' % mtype)
                measurement_info = handler.run()
                measurement_info['measurement_type'] = mtype
                self.received_traces.append(measurement_info)

            self.logger.info('receiving network statuses')
            self.network_statuses = MUtils.read_compressed_json(self.fd)

            self.logger.info('saving results')
            self.save_results()

        except (socket.error, socket.timeout) as e:
            self.logger.error('connection failed (%s)' % e)
        except zlib.error as e:
            self.logger.error('communication error (%s)' % e)
        except OSError as e:
            self.logger.error('internal error (%s)' % e)
        finally:
            self.server.measurement_has_finished()

    def finish(self):
        self.logger.info('closing connection')
        self.fd.close()

    def save_results(self):

        results = {}
        results['client_info'] = self.client_info
        results['client_address'] = {'IP': self.client_address[0], 'port': self.client_address[1]}
        results['traceroute'] = self.traceroute.to_json()
        results['remote_traceroute'] = self.remote_traceroute
        results['received_traces'] = self.received_traces
        results['network_statuses'] = self.network_statuses

        # Write the final json
        log_file = None
        try:
            log_filepath = os.path.join(self.measurement_dir, 'log.json')
            self.logger.info('writing log file %s' % log_filepath)
            log_file = open(log_filepath, 'w')
            json.dump(results, log_file)
        except IOError as e:
            self.logger.error('failed to write log file %s: %s' % (log_filepath, e))
        finally:
            if log_file: log_file.close()

class VoIPerfServer(SocketServer.ThreadingMixIn, SocketServer.TCPServer):

    allow_reuse_address = True

    def __init__(self, server_address, output_dir, max_measurements):

        self.logger = logging.getLogger('VoIPerfServer:%s' % server_address[1])
        self.logger.info('init')
        
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)
        self.output_dir = output_dir

        self.max_measurements = max_measurements
        self.running_measurements = 0
        self.running_measurements_lock = threading.Lock()

        SocketServer.TCPServer.__init__(self, server_address, MeasurementHandler)

    def server_activate(self):
        self.logger.info('listening')
        SocketServer.TCPServer.server_activate(self)

    def can_start_measurement(self):
        with self.running_measurements_lock:
            if self.running_measurements < self.max_measurements:
                self.running_measurements += 1
                self.logger.debug('current running measurements: %s' % self.running_measurements)
                return True
            return False

    def measurement_has_finished(self):
        with self.running_measurements_lock:
            self.running_measurements -= 1
            self.logger.debug('current running measurements: %s' % self.running_measurements)
            assert self.running_measurements >= 0

    def handle_error(self, request, client_address):
        trace = traceback.format_exc()
        self.logger.error('Measurement handler (%s:%s) died' % client_address)
        for line in trace.split('\n'):
            if line:
                self.logger.error('Measurement handler (%s:%s): %s' % (client_address[0],
                                                                       client_address[1], line))
