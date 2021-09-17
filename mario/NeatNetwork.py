from collections import defaultdict

import json
import math
import enum
import networkx as nx
import numpy as np
from typing import Dict, List, Set, Tuple
from networkx.algorithms.dag import descendants
from numpy import ndarray


def sigmoidal(x: float):
    # print(x)
    if (x < -4):
        x = -4
    elif x > 4:
        x = 4

    return 1 / (1 + math.exp(-4.9*x))


class NodeLocation:
    x: int
    y: int
    z: int

    def __init__(self, x, y, z):
        self.x = x
        self.y = y
        self.z = z

    def __str__(self):
        return str((self.x, self.y, self.z))

    def __repr__(self) -> str:
        return str((self.x, self.y, self.z))

    def __hash__(self) -> int:
        return ((self.x, self.y, self.z)).__hash__()

    def __eq__(self, other):
        return self.__dict__ == other.__dict__


class ConnectionLocation:
    x1: int
    y1: int
    z1: int
    x2: int
    y2: int
    z2: int
    weight: float

    def __init__(self, x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, weight: float):
        self.x1 = x1
        self.y1 = y1
        self.z1 = z1
        self.x2 = x2
        self.y2 = y2
        self.z2 = z2
        self.weight = weight


# Identify input, hidden and output nodes
def constructNetwork(nodes: List[NodeLocation], connections: List[ConnectionLocation], bias: NodeLocation = None):
    nodeValuePre: Dict[NodeLocation, float] = dict()
    nodeValuePost: Dict[NodeLocation, float] = dict()
    # computationOrder: List[NodeLocation] = list()
    # ndarray()
    inputNodes = list(filter(lambda n: n.z == 0, nodes))
    # if bias is not None:
    #     inputNodes.append(bias)
    outputNodes = list(filter(lambda n: n.z == 3, nodes))
    print("Node values initializing...")
    print("input nodes:")
    print(len(inputNodes))
    print("outputnodes")
    print(len(outputNodes))
    for n in inputNodes:
        nodeValuePre[n] = 0
        nodeValuePost[n] = 0
    for n in outputNodes:
        nodeValuePre[n] = 0
        nodeValuePost[n] = 0
    # for node in nodes:
    #     nodeValuePre[node] = 0
    #     nodeValuePost[node] = 0
    print("constructing graph representation")
    # data = list(map(lambda c: (NodeLocation(c.x1, c.y1, c.z1),
    #             NodeLocation(c.x2, c.y2, c.z2), c.weight), connections))
    print("construct graph")
    graph = nx.MultiDiGraph()
    for c in connections:
        source = NodeLocation(c.x1, c.y1, c.z1)
        target = NodeLocation(c.x2, c.y2, c.z2)
        graph.add_edge(source,
                       target, weight=c.weight)
        nodeValuePre[source] = 0
        nodeValuePost[source] = 0
        nodeValuePre[target] = 0
        nodeValuePost[target] = 0
    print("Constructing topological order...")
    computationOrder = list(
        nx.topological_generations(graph))
    print("Constructed Computable Network...")
    # print(NodeLocation(0,0,0) == NodeLocation(0,0,0))
    # for n in inputNodes:
    # print(graph.has_node(n))
    # print(graph.has_node(outputNodes[0]))
    # nx.has_path(graph,n, outputNodes[0])
    return ComputableNetwork(computationOrder, nodeValuePre, nodeValuePost, inputNodes, outputNodes, graph)


class ComputableNetwork:
    computationOrder: List[List[NodeLocation]]
    nodeValuePre: Dict[NodeLocation, float]
    nodeValuePost: Dict[NodeLocation, float]
    # nodeMap: Dict[NodeLocation, List[Tuple[NodeLocation, ConnectionLocation]]]
    inputNodes: List[NodeLocation]
    outputNodes: List[NodeLocation]
    graph: nx.MultiDiGraph
    inputNdArray: ndarray

    def __init__(self, computationOrder: List[List[NodeLocation]],
                 nodeValuePre: Dict[NodeLocation, float],
                 nodeValuePost: Dict[NodeLocation, float],
                 #  nodeMap: Dict[NodeLocation, List[Tuple[NodeLocation, ConnectionLocation]]],
                 inputNodes: List[NodeLocation],
                 outputNodes: List[NodeLocation],
                 graph: nx.MultiDiGraph):
        self.computationOrder = computationOrder
        # self.nodeMap = nodeMap
        self.nodeValuePre = nodeValuePre
        self.nodeValuePost = nodeValuePost
        self.inputNodes = inputNodes
        self.outputNodes = outputNodes
        self.graph = graph

    def input(self, input: ndarray):
        self.inputNdArray = input
        # print(self.inputNdArray)
        # for x in range(0, xSize):
        #     for y in range(0, ySize):
        #         for z in range(0, zSize):
        #             self.nodeValuePre[NodeLocation(
        #                 x, y, z)] = input.item((x, y, z))

    def compute(self):
        for computationSet in self.computationOrder:
            # print("Processing Generation:")
            # print(computationSet)
            for source in computationSet:
                if source.z > 0:
                    self.activateNode(source)
                    self.nodeValuePre[source] = 0
                descendants = self.graph.neighbors(source)
                # print("from: " + str(source) + ": Activated")
                # print(self.nodePostValue(source))

                # print("\t" + str(descendants))
                for target in descendants:
                    # print(self.graph.has_edge(source, target))
                    weight = self.graph.get_edge_data(
                        source, target)[0]["weight"]
                    # apply activation on every types aside from the inputs
                    # z 0-2 are input channels
                    # print(weight)

                    self.nodeValuePre[target] += self.nodePostValue(
                        source) * weight
        # for outputNode in self.outputNodes:
        #     self.activateNode(outputNode)
            # self.nodeValuePre[source] = 0

    def nodePostValue(self, node: NodeLocation):
        if (node.z == 0):
            return self.inputNdArray.item((node.x, node.y))
        else:
            return self.nodeValuePost[node]

    def nodePreValue(self, node: NodeLocation):
        if (node.z == 0):
            return self.inputNdArray.item((node.x, node.y))
        else:
            return self.nodeValuePre[node]

    def activateNode(self, node: NodeLocation):
        value = self.nodeValuePre[node]
        if (node.z == 0):
            value = self.inputNdArray.item((node.x, node.y))
        self.nodeValuePost[node] = sigmoidal(value)

    def output(self) -> List[float]:
        return list(map(lambda n: self.nodeValuePost[n], self.outputNodes))

    def draw(self):
        nx.draw_spring(self.graph)

    def write(self):
        nx.write_edgelist(self.graph, "test.txt")
