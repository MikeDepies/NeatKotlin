from collections import defaultdict

import json
import math
import enum
from time import sleep
import networkx as nx
import numpy as np
from typing import Dict, List, Set, Tuple
from networkx.algorithms.dag import descendants
from numpy import ndarray, vectorize


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

def getConnectionIndex(source: NodeLocation, target: NodeLocation):
        if source.z == 0 and target.z == 1:
            return 0
        if source.z == 1 and target.z == 2:
            return 1
        if source.z == 1 and target.z == 6:
            return 2
        if source.z == 2 and target.z == 6:
            return 3
        if source.z == 2 and target.z == 3:
            return 4
        if source.z == 3 and target.z == 4:
            return 5
        if source.z == 3 and target.z == 6:
            return 6
        if source.z == 4 and target.z == 5:
            return 7
        if source.z == 4 and target.z == 6:
            return 8
        if source.z == 5 and target.z == 6:
            return 9
        if source.z == 2 and target.z == 2:
            return 10
        if source.z == 3 and target.z == 3:
            return 11
        if source.z == 4 and target.z == 4:
            return 12
        if source.z == 5 and target.z == 5:
            return 13
        if source.z == 6 and target.z == 6:
            return 14
        if source.z == 6 and target.z == 5:
            return 15
        if source.z == 6 and target.z == 4:
            return 16
        if source.z == -2 and target.z == 1:
            return 17
        if source.z == -2 and target.z == 2:
            return 18
        if source.z == -2 and target.z == 3:
            return 19
        if source.z == -2 and target.z == 4:
            return 20
        if source.z == -2 and target.z == 5:
            return 21
        if source.z == -2 and target.z == 6:
            return 22
        else:
            print("test???")
# Identify input, hidden and output nodes
def constructNetwork(nodes: List[NodeLocation], connections: List[ConnectionLocation], layerShapes: List[List[int]], bias: NodeLocation = None):
    
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
    
    # for node in nodes:
    #     nodeValuePre[node] = 0
    #     nodeValuePost[node] = 0
    print("constructing graph representation")
    # data = list(map(lambda c: (NodeLocation(c.x1, c.y1, c.z1),
    #             NodeLocation(c.x2, c.y2, c.z2), c.weight), connections))
    print("construct graph")
    connection = [np.zeros(layerShapes[1] + layerShapes[0]),
                np.zeros(layerShapes[2] + layerShapes[1]),
                np.zeros(layerShapes[6] + layerShapes[1]),
                np.zeros(layerShapes[6] + layerShapes[2]),
                np.zeros(layerShapes[3] + layerShapes[2]),
                np.zeros(layerShapes[4] + layerShapes[3]),
                np.zeros(layerShapes[6] + layerShapes[3]),
                np.zeros(layerShapes[5] + layerShapes[4]),
                np.zeros(layerShapes[6] + layerShapes[4]),
                np.zeros(layerShapes[6] + layerShapes[5]),
                np.zeros(layerShapes[2] + layerShapes[2]),
                np.zeros(layerShapes[3] + layerShapes[3]),
                np.zeros(layerShapes[4] + layerShapes[4]),
                np.zeros(layerShapes[5] + layerShapes[5]),
                np.zeros(layerShapes[6] + layerShapes[6]),
                np.zeros(layerShapes[5] + layerShapes[6]),
                np.zeros(layerShapes[4] + layerShapes[6]),
                np.zeros(layerShapes[1]),
                np.zeros(layerShapes[2]),
                np.zeros(layerShapes[3]),
                np.zeros(layerShapes[4]),
                np.zeros(layerShapes[5]),
                np.zeros(layerShapes[6]),
                ]
    # print(connection[0])
    values = [np.zeros([*layerShapes[0], 2]),
            np.zeros([*layerShapes[1], 2]),
            np.zeros([*layerShapes[2], 2]),
            np.zeros([*layerShapes[3], 2]),
            np.zeros([*layerShapes[4], 2]),
            np.zeros([*layerShapes[5], 2]),
            np.zeros([*layerShapes[6], 2])]
    
    for c in connections:
        source = NodeLocation(c.x1, c.y1, c.z1)
        target = NodeLocation(c.x2, c.y2, c.z2)
        connectionIndex = getConnectionIndex(source, target)
        
        try:
            if source.z == -2: # Bias only has target dimensions
                connection[connectionIndex][target.y, target.x ] = c.weight
            else:
                connection[connectionIndex][target.y, target.x, source.y, source.x ] = c.weight
        except:
            print(str(source) + " to " + str(target) + " = " + str(connectionIndex))
    print("Constructing topological order...")
    print("Constructed Computable Network...")
    # [15, 16]
    
    # print(computationOrder[0])
    
    
                # if (array[source.y, source.x, target.y, target.x] != 0):
                #     print(array[source.y, source.x, target.y, target.x])
    # print("===0===")
    # print(connection[0])
    # print("===1===")
    # print(connection[1])
    # print("===2===")
    # print(connection[2])
    # print("===3s===")
    # print(connection[3])
    # sleep(10000)
    # for v in t:
    #     print(connection[0][v[0], v[1], v[2], v[3]])
    # print(connection[2])
    # print(NodeLocation(0,0,0) == NodeLocation(0,0,0))
    # for n in inputNodes:
    # print(graph.has_node(n))
    # print(graph.has_node(outputNodes[0]))
    # nx.has_path(graph,n, outputNodes[0])
    return ComputableNetwork(connection, values)


class ComputableNetwork:
    # nodeMap: Dict[NodeLocation, List[Tuple[NodeLocation, ConnectionLocation]]]
    
    inputNdArray: ndarray
    connection: List[ndarray]
    values: List[ndarray]

    def __init__(self, 
                connection: List[ndarray],
                values: List[ndarray]):

        # self.nodeMap = nodeMap
        
        self.connection = connection
        self.values = values

    def input(self, input: ndarray):
        self.inputNdArray = input / 255.0
        self.values[0][...,0] = self.inputNdArray
        self.values[0][...,1] = self.inputNdArray
        # print(self.inputNdArray)
        # for x in range(0, xSize):
        #     for y in range(0, ySize):
        #         for z in range(0, zSize):
        #             self.nodeValuePre[NodeLocation(
        #                 x, y, z)] = input.item((x, y, z))

    def compute(self):


        # connection = [np.zeros(layerShapes[1] + layerShapes[0]),
        #               np.zeros(layerShapes[2] + layerShapes[1]),
        #               np.zeros(layerShapes[6] + layerShapes[1]),
        #               np.zeros(layerShapes[6] + layerShapes[2]),
        #               np.zeros(layerShapes[3] + layerShapes[2]),
        #               np.zeros(layerShapes[4] + layerShapes[3]),
        #               np.zeros(layerShapes[6] + layerShapes[3]),
        #               np.zeros(layerShapes[5] + layerShapes[4]),
        #               np.zeros(layerShapes[6] + layerShapes[4]),
        #               np.zeros(layerShapes[6] + layerShapes[5]),
        #               np.zeros(layerShapes[2] + layerShapes[2]),
        #               np.zeros(layerShapes[3] + layerShapes[3]),
        #               np.zeros(layerShapes[4] + layerShapes[4]),
        #               np.zeros(layerShapes[5] + layerShapes[5]),
        #               ]
        # # print(connection[0])
        # values = [np.zeros([*layerShapes[0], 2]),
        #           np.zeros([*layerShapes[1], 2]),
        #           np.zeros([*layerShapes[2], 2]),
        #           np.zeros([*layerShapes[3], 2]),
        #           np.zeros([*layerShapes[4], 2]),
        #           np.zeros([*layerShapes[5], 2]),
        #           np.zeros([*layerShapes[6], 2])]
        vectorizedSigmoidal = np.vectorize(sigmoidal)
        # layer 1
        v1: ndarray = (self.inputNdArray * self.connection[0]).sum((2, 3))
        self.values[1][..., 0] = v1 + self.connection[17]
        self.values[1][..., 1] = vectorizedSigmoidal(v1)

        # layer 2
        v2: ndarray = (self.values[1][..., 1] * self.connection[1]).sum((2, 3))
        self.values[2][..., 0] = v2 + (self.values[2][..., 1] * self.connection[10]).sum((2, 3))  + self.connection[18]
        self.values[2][..., 1] = vectorizedSigmoidal(self.values[2][..., 0])
        
        # layer 3
        v3: ndarray = (self.values[2][..., 1] * self.connection[4]).sum((2, 3))
        self.values[3][..., 0] = v3 + (self.values[3][..., 1] * self.connection[11]).sum((2, 3)) +  + self.connection[19]
        self.values[3][..., 1] = vectorizedSigmoidal(self.values[3][..., 0])
        
        # layer 4
        v4: ndarray = (self.values[3][..., 1] * self.connection[5]).sum((2, 3))
        # print(self.values[5][..., 1].shape)
        # print(self.connection[16].sum((2, 3)).shape)
        # print((self.values[5][..., 1].shape))
        # print(self.connection[9].sum((2, 3)).shape)
        self.values[4][..., 0] = v4 + (self.values[4][..., 1] * self.connection[12]).sum((2, 3)) + (self.values[6][..., 1] * self.connection[16]).sum((2, 3))  + self.connection[20]
        self.values[4][..., 1] = vectorizedSigmoidal(self.values[4][..., 0])
        
        # layer 5
        v4: ndarray = (self.values[4][..., 1] * self.connection[7]).sum((2, 3))
        self.values[5][..., 0] = v4 + (self.values[5][..., 1] * self.connection[13]).sum((2, 3)) + (self.values[6][..., 1] * self.connection[15]).sum((2, 3))  + self.connection[21]
        self.values[5][..., 1] = vectorizedSigmoidal(self.values[5][..., 0])


        v6: ndarray = (self.values[1][..., 1] * self.connection[2]).sum((2, 3))
        v7: ndarray = (self.values[2][..., 1] * self.connection[3]).sum((2, 3))
        v8: ndarray = (self.values[3][..., 1] * self.connection[6]).sum((2, 3))
        v9: ndarray = (self.values[4][..., 1] * self.connection[8]).sum((2, 3))
        v10: ndarray = (self.values[5][..., 1] * self.connection[9]).sum((2, 3))
        vSelf: ndarray = (self.values[6][..., 1] * self.connection[14]).sum((2, 3))
        sum = v10 + v6 + v7 + v8 + v9 + vSelf  + self.connection[22]
        # print("=========")
        # print(v3)
        # print(self.connection[2])
        # print(v4)
        # print(self.connection[3])
        # print("=========")
        self.values[6][..., 0] = sum
        self.values[6][..., 1] = vectorizedSigmoidal(sum)
        # for v in self.values:
        #     print(v[...,1])
        # for computationSet in self.computationOrder:
        #     # print("Processing Generation:")
        #     # print(computationSet)
        #     # print(v)
        #     # print(vectorizedSigmoidal(v))
        #     for source in computationSet:
        #         if source.z > 0:
        #             self.activateNode(source)
        #             self.nodeValuePre[source] = 0
        #         descendants = self.graph.neighbors(source)
        #         # print("from: " + str(source) + ": Activated")
        #         # print(self.nodePostValue(source))
        #         # if source.z ==0:
        #         #     print(str(source) + ": " + str(self.nodePostValue(source)))
        #         # print("\t" + str(descendants))

        #         for target in descendants:
        #             # print(self.graph.has_edge(source, target))
        #             weight = self.graph.get_edge_data(
        #                 source, target)[0]["weight"]
        #             # apply activation on every types aside from the inputs
        #             # z 0-2 are input channels
        #             # print(weight)

        #             self.nodeValuePre[target] += self.nodePostValue(
        #                 source) * weight
        # for outputNode in self.outputNodes:
        #     self.activateNode(outputNode)
        # self.nodeValuePre[source] = 0

    def nodePostValue(self, node: NodeLocation):
        if (node.z == 0):
            return self.inputNdArray.item((node.y, node.x))
        else:
            return self.nodeValuePost[node]

    def nodePreValue(self, node: NodeLocation):
        if (node.z == 0):
            return self.inputNdArray.item((node.y, node.x))
        else:
            return self.nodeValuePre[node]

    def activateNode(self, node: NodeLocation):
        value = self.nodeValuePre[node]
        if (node.z == 0):
            value = self.inputNdArray.item((node.y, node.x))
        self.nodeValuePost[node] = sigmoidal(value)

    def output(self) -> ndarray:
        return self.values[6][..., 1]

    def draw(self):
        nx.draw_spring(self.graph)

    def write(self):
        nx.write_edgelist(self.graph, "test.txt")
