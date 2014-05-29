#!/usr/bin/env python

# vim: columns=100
# vim: expandtab softtabstop=4 shiftwidth=4 tabstop=4

SEQ_SIZE = 4       # A 4 bytes big endian integer
TIMESTAMP_SIZE = 8 # A 8 bytes big endian integer (that is, a Java long)
FOOTER_SIZE = SEQ_SIZE + TIMESTAMP_SIZE

import struct

class PacketFooter:
    def __init__(self, seq, timestamp):
        self.seq = seq
        self.timestamp = timestamp

def write(output, seq, timestamp):
    if len(output) < FOOTER_SIZE:
        return output
    p = struct.pack('>iq', seq, timestamp)
    return output[:-FOOTER_SIZE] + p

def load(input):
    if len(input) < FOOTER_SIZE:
        return None
    p = struct.unpack('>iq', input[-FOOTER_SIZE:])
    return PacketFooter(int(p[0]), int(p[1]))

def extract(input):
    if len(input) < FOOTER_SIZE:
        return None
    return input[-FOOTER_SIZE:]
