#!/usr/bin/env python

# vim: columns=100 
# vim: expandtab softtabstop=4 shiftwidth=4 tabstop=4

import os
import struct

import PacketFooter
import RTPHeader

def write_v1_trace((preamble, packets), output_stream, randomize = False):

    # write the preamble first
    try:
        if 'version' not in preamble:
            preamble['version'] = 1
        elif preamble['version'] != 1:
            raise ValueError('invalid trace version (got %s)' % preamble['version'])
        p = struct.pack('>iiH', preamble['version'], preamble['id'], preamble['dport'])
        output_stream.write(p)
    except KeyError as e:
        raise ValueError('invalid trace preamble (key %s not found)' % e)

    # write each packet
    try:
        for packet in packets:
            output_stream.write(struct.pack('>dH', packet['timestamp'], packet['length']))
            if randomize:
                packet['payload'] = os.urandom(len(packet['payload']))
            output_stream.write(packet['payload'])
    except struct.error as e:
        raise ValueError('invalid trace packet (%s)' % e)
    except KeyError as e:
        raise ValueError('invalid trace packet (key %s not found)' % e)

def write_v2_trace((preamble, packets), output_stream, randomize = False):

    # write the preamble first
    try:
        if 'version' not in preamble:
            preamble['version'] = 2
        elif preamble['version'] != 2:
            raise ValueError('invalid trace version (got %s)' % preamble['version'])
        p = struct.pack('>iiH', preamble['version'], preamble['id'], preamble['dport'])
        output_stream.write(p)
    except KeyError as e:
        raise ValueError('invalid trace preamble (key %s not found)' % e)

    # write each packet
    try:
        for packet in packets:
            output_stream.write(struct.pack('>dH', packet['timestamp'], packet['length']))
            if randomize:
                packet['payload'] = os.urandom(len(packet['payload']))
            output_stream.write(packet['payload'])
    except struct.error as e:
        raise ValueError('invalid trace packet (%s)' % e)
    except KeyError as e:
        raise ValueError('invalid trace packet (key %s not found)' % e)

def write_rtp_trace((preamble, packets), output_stream, randomize = False):

    # write the preamble first
    try:
        if 'version' not in preamble:
            preamble['version'] = 3
        elif preamble['version'] != 3:
            raise ValueError('invalid trace version (got %s)' % preamble['version'])
        p = struct.pack('>iiH', preamble['version'], preamble['id'], preamble['dport'])
        output_stream.write(p)
    except KeyError as e:
        raise ValueError('invalid trace preamble (key %s not found)' % e)

    # write each packet
    try:
        for i, packet in enumerate(packets, 1):
            if len(packet['payload']) < RTPHeader.SIZE:
                raise ValueError('packet n.%s does not contain an RTP header' % i)
            output_stream.write(struct.pack('>dH', packet['timestamp'], packet['length']))
            if randomize:
                packet['payload'] = os.urandom(len(packet['payload']))
            output_stream.write(packet['payload'])
    except struct.error as e:
        raise ValueError('invalid trace packet (%s)' % e)
    except KeyError as e:
        raise ValueError('invalid trace packet (key %s not found)' % e)

def read_v1_trace(input_stream):
    try:
        # read the preamble first
        p = struct.unpack('>iiH', input_stream.read(4 + 4 + 2))
        if p[0] != 1:
            raise ValueError('invalid trace version (got %s, was expecting 1)' % p[0])
        preamble = {}
        preamble['version'] = p[0]
        preamble['id'] = p[1]
        preamble['dport'] = p[2]

        # read each packet
        packets = []
        while True:
            buf = input_stream.read(8 + 2)
            if not buf:
                break

            packet = {}
            packet['timestamp'], packet['length'] = struct.unpack('>dH', buf)
            packet['payload'] = input_stream.read(packet['length'])

            packets.append(packet)
    
        return preamble, packets

    except struct.error as e:
        raise ValueError('unexpected end of trace (%s)' % e)

def read_v2_trace(input_stream):
    try:
        # read the preamble first
        p = struct.unpack('>iiH', input_stream.read(4 + 4 + 2))
        if p[0] != 2:
            raise ValueError('invalid trace version (got %s, was expecting 1)' % p[0])
        preamble = {}
        preamble['version'] = p[0]
        preamble['id'] = p[1]
        preamble['dport'] = p[2]

        # read each packet
        packets = []
        while True:
            buf = input_stream.read(8 + 2)
            if not buf:
                break

            packet = {}
            packet['timestamp'], packet['length'] = struct.unpack('>dH', buf)
            packet['payload'] = input_stream.read(packet['length'])
            packet['footer'] = PacketFooter.load(packet['payload'])

            packets.append(packet)
    
        return preamble, packets

    except struct.error as e:
        raise ValueError('unexpected end of trace (%s)' % e)

def read_rtp_trace(input_stream):
    try:
        # read the preamble first
        p = struct.unpack('>iiH', input_stream.read(4 + 4 + 2))
        if p[0] != 3:
            raise ValueError('invalid trace version (got %s, was expecting 1)' % p[0])
        preamble = {}
        preamble['version'] = p[0]
        preamble['id'] = p[1]
        preamble['dport'] = p[2]

        # read each packet
        packets = []
        i = 0
        while True:

            i += 1

            buf = input_stream.read(8 + 2)
            if not buf:
                break

            packet = {}
            packet['timestamp'], packet['length'] = struct.unpack('>dH', buf)
            packet['payload'] = input_stream.read(packet['length'])
            if len(packet['payload']) < RTPHeader.SIZE:
                raise ValueError('packet n.%s does not contain an RTP header' % i)

            packets.append(packet)
    
        return preamble, packets

    except struct.error as e:
        raise ValueError('unexpected end of trace (%s)' % e)

def read_trace(input_stream):

    # read the trace version and call the appropriate read function
    try:
        # lookup the trace version and use the relative reader
        position = input_stream.tell()
        version = struct.unpack('>i', input_stream.read(4))[0]
        input_stream.seek(position)
        if version == 1:
            return read_v1_trace(input_stream)
        elif version == 2:
            return read_v2_trace(input_stream)
        elif version == 3:
            return read_rtp_trace(input_stream)
        else:
            raise ValueError('unknown trace version %d' % version)
    except struct.error as e:
        raise ValueError('unexpected end of trace (%s)' % e)
