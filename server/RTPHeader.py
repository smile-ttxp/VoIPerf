#!/usr/bin/env python

# vim: columns=100
# vim: expandtab softtabstop=4 shiftwidth=4 tabstop=4

import struct

SIZE = 12 # size (in bytes) of the RTP header

def get_sequence_number(rtp_packet):
    if len(rtp_packet) < SIZE:
        return None
    return struct.unpack('>H', rtp_packet[2:4])[0]

def get_timestamp(rtp_packet):
    if len(rtp_packet) < SIZE:
        return None
    return struct.unpack('>I', rtp_packet[4:8])[0]
