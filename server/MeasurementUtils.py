#!/usr/bin/env python

# vim: columns=100
# vim: expandtab softtabstop=4 shiftwidth=4 tabstop=4

import datetime
import json
import socket
import time
import zlib

# Local imports
import Config

def read_compressed_string(fd):
    length = fd.readline()
    if not length:
        raise socket.error('connection closed by remote host')
    length = int(length)
    if not length:
        return {}
    return zlib.decompress(fd.read(length), Config.ZLIB_MAGIC_NUMBER)

def read_compressed_json(fd):
    return json.loads(read_compressed_string(fd))

def read_json(fd):
    length = fd.readline()
    if not length:
        raise socket.error('connection closed by remote host')
    length = int(length)
    return json.loads(fd.read(length))

def send_json(fd, json_object):
    s = json.dumps(json_object)
    fd.write(str(len(s)) + '\n')
    fd.write(s)
    fd.flush()

def busywait_sleep(sec):
    start = datetime.datetime.now()
    curr = datetime.datetime.now()
    while (curr - start).total_seconds() < sec:
        time.sleep(0.001)
        curr = datetime.datetime.now()
