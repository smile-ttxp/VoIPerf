#!/usr/bin/env python

# vim: columns=100
# vim: expandtab softtabstop=4 shiftwidth=4 tabstop=4

VERSION = "2013-11-22_alpha"

TRACES_DIR = '../traces' # directory where the traces to be sent are stored

DEFAULT_SERVER_LISTEN_PORT = 12345   # port where the measurement server listens for
DEFAULT_MAX_MEASUREMENTS = 3         # maximum number of concurrent measurements
DEFAULT_OUTPUT_DIRECTORY = 'results' # directory where the measurement results are stored

CAPTURE_INTERFACE = 'eth0' # interface to capture the incoming trace packets from

DEFAULT_CLIENT_IDLE_TIMEOUT = 60*5 # Timeout (in seconds) before an idle connection gets closed

TRACE_WAIT_TIMEOUT = 120      # timeout (in seconds) before the first trace packet is received
TRACE_RECV_TIMEOUT = 3       # timeout (in seconds) after the first trace packet is received
HOLE_PUNCHING_TIMEOUT = 10   # timeout (in seconds) for waiting for a hole punching packet
TRACE_SEND_BSIZE = 1024**2   # send buffer size
TRACE_RECV_BSIZE = 1024**2   # receive buffer size
TRACE_MAX_PACKET_SIZE = 1500 # maximum trace packet size (in bytes)

MEASUREMENT_SERVER_BUSY_MSG = 'BUSY'
MEASUREMENT_SERVER_AVAILABLE_MSG = 'OK'

# Don't ask me why we need this to decompress gzip streams with zlib.
# Read this instead:
# http://stackoverflow.com/questions/1838699/how-can-i-decompress-a-gzip-stream-with-zlib
ZLIB_MAGIC_NUMBER = 15 + 32

# Protocol messages
TRACE_END_MESSAGE = 'QUIT'          # message to be used to notify the trace has finished
HOLE_PUNCHING_PACKET = 'I HATE NAT' # payload of UDP packets used for hole punching
END_MEASUREMENTS = 'END'            # message a client uses to notify measurements have finished

# Measurement types
RTP_SEND_MEASUREMENT_TYPE = 'RTP_SEND'       # the client sends an RTP trace
RTP_RECV_MEASUREMENT_TYPE = 'RTP_RECV'       # the client receives an RTP trace
RANDOM_SEND_MEASUREMENT_TYPE = 'RANDOM_SEND' # the client sends a generic trace
RANDOM_RECV_MEASUREMENT_TYPE = 'RANDOM_RECV' # the client receives a generic trace

# Trace statistics keys
PACKET_LOSS_STAT = 'packet_loss'
AVG_RATE_STAT = 'avg_rate_kbits'
