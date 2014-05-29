#!/usr/bin/env python

# vim: columns=100
# vim: expandtab softtabstop=4 shiftwidth=4 tabstop=4

import argparse
import logging
import os
import sys

# Local imports
import Config
import VoIPerfServer

def main(argv = sys.argv):

    parser = argparse.ArgumentParser(description = 'VoIPerf stand-alone server')
    parser.add_argument('-p', '--port', metavar = 'PORT', dest = 'listen_port',
                        type = int, default = Config.DEFAULT_SERVER_LISTEN_PORT,
                        help = 'listen for client connections on this port')
    parser.add_argument('-m', '--max_measurements', metavar = 'N', dest = 'max_measurements',
                        type = int, default = Config.DEFAULT_MAX_MEASUREMENTS,
                        help = 'max number of concurrent measurements')
    parser.add_argument('-o', '--output_dir', metavar = 'DIR', dest = 'output_dir', type = str,
                        default = Config.DEFAULT_OUTPUT_DIRECTORY,
                        help = 'directory where the results are stored')
    parser.add_argument('-i', '--iface', metavar = 'INTERFACE',
                        dest = 'capture_interface', type = str,
                        default = Config.CAPTURE_INTERFACE,
                        help = 'interface to capture the incoming trace packets from')
    parser.add_argument('-l', '--log_file', metavar = 'FILENAME', dest = 'logfile', type = str,
                        default = None, help = 'log file name')
    args = parser.parse_args()

    if os.geteuid() != 0:
        print >> sys.stderr, 'must be run as root'
        return 1

    Config.CAPTURE_INTERFACE = args.capture_interface

    log_format = '[%(levelname)s] %(created)s %(name)s %(message)s'
    if args.logfile:
        logging.basicConfig(format = log_format, level = logging.DEBUG, filename = args.logfile)
    else:
        logging.basicConfig(format = log_format, level = logging.DEBUG)
    logging.info('VERSION: %s' % Config.VERSION)

    server = VoIPerfServer.VoIPerfServer(('', args.listen_port),
                                         args.output_dir,
                                         args.max_measurements)

    server.serve_forever()

    return 0

if __name__ == '__main__':
    sys.exit(main())
