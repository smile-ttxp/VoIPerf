#!/usr/bin/env python

# vim: columns=100
# vim: expandtab softtabstop=4 shiftwidth=4 tabstop=4

import json
import os
import re
import signal
import subprocess
import threading
import time

class Tcpdump:

    def __init__(self, interface, address, port, outfilename):
        self.interface = interface
        self.address = address
        self.port = port
        self.outfilename = outfilename

        self.pid_available = threading.Event()

    def run(self):
        self.start_time = time.time()
        p = subprocess.Popen(['tcpdump', '-w', self.outfilename,
                              '-i', self.interface,
                              'host', self.address, 'and',
                              'port', str(self.port), 'and', 'udp'],
                              stdout = subprocess.PIPE, stderr = subprocess.PIPE)
        self.pid = p.pid
        self.pid_available.set()

        self.out, self.err = p.communicate()
        self.ret = p.returncode

    def make_thread(self):
        return threading.Thread(target = self.run)

    def kill(self):
        self.pid_available.wait()
        os.kill(self.pid, signal.SIGTERM)

    def parse(self):
        result = {}
        result['start_time'] = self.start_time
        result['ret'] = self.ret
        result['interface'] = self.interface
        result['stdout'] = self.out.splitlines()
        result['stderr'] = self.err.splitlines()
        result['address'] = self.address
        result['port'] = self.port
        result['output_file'] = self.outfilename
        return result

    def __str__(self):
        return json.dumps(self.parse())

class TraceRoute:

    def __init__(self, remote_address):
        self.remote_address = remote_address
        self.out, self.err, self.ret = None, None, None

    def run(self):
        self.start_time = time.time()
        p = subprocess.Popen(['traceroute', self.remote_address],
                stdout = subprocess.PIPE, stderr = subprocess.PIPE)
        self.out, self.err = p.communicate()
        self.ret = p.returncode

    def parse(self):

        result = {}

        result['start_time'] = self.start_time
        result['ret'] = self.ret
        result['stderr'] = self.err.splitlines()

        lines = self.out.splitlines()
        if len(lines) > 0:
            match = re.match('traceroute to (?P<host>\S+) \((?P<IP>\S+)\), '
                        '(?P<nhops_max>\d+) hops max, '\
                        '(?P<packet_size>\d+) byte packets', lines[0])
            if not match:
                return None
            for k, v in match.groupdict().items():
                result[k] = v

        hops = []
        for line in lines[1:]:
            match = re.match('\s*(?P<hop_number>\d+)\s+'
                        '((?P<host>\S+) \((?P<IP>\S+)\)\s+(?P<rtt>\S+) ms|(?P<uknown>\*))', line)
            if not match:
                return None
            match = match.groupdict()
            match['line'] = line
            hops.append(match)
        result['hops'] = hops

        return result

    def to_json(self):
        result = self.parse()
        if not result:
            result = {'start_time': self.start_time,\
                      'ret': self.ret,\
                      'stderr': self.err.splitlines(),\
                      'stdout': self.out.splitlines()}
        return result

    def __str__(self):
        return json.dumps(self.to_json())

class Ping:

    def __init__(self, remote_address):
        self.remote_address = remote_address
        self.out, self.err, self.ret = None, None, None

    def run(self):
        self.start_time = time.time() * 1000
        p = subprocess.Popen(['ping', '-c', '10', '%s' % self.remote_address],
                stdout = subprocess.PIPE, stderr = subprocess.PIPE)
        self.out, self.err = p.communicate()
        self.ret = p.returncode

    def to_json(self):
        result = {'start_time': self.start_time,\
                  'out': self.out.splitlines(),\
                  'err': self.err.splitlines(),\
                  'ret': self.ret}
        return result

    def __str__(self):
        return json.dumps(self.to_json())

if __name__ == '__main__':
    td = Tcpdump('eth0', '127.0.0.1', '1234', 'tmpdump')
    th = td.make_thread()
    th.start()
    time.sleep(3)
    td.kill()
    th.join()
    print td
