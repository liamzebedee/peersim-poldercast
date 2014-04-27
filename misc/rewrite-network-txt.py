# Converts the huge 5GB network.txt file from the Twitter pubsub relationships dataset https://wiki.engr.illinois.edu/display/forward/Dataset-UDI-TwitterCrawl-Aug2012 into a BSON structure and stores only about 100K of the original nodes
# This is for memory reasons.

import bson

# in-format
# stringint => stringint

# out-format
#  int => int[]

data = {}

with open("network.txt") as infile:
	for i, line in enumerate(infile):
		parts = line.split("\t")
		nodeA = int(parts[0])
		nodeB = int(parts[1])
		if nodeA in data:
			data[nodeA].append(nodeB)
		else:
			data[nodeA] = [nodeB]
		print(len(data))
		if len(data) > 100000: break

bson.serialize_to_stream(data, open("network.bson", 'wb'))
