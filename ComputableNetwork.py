from dataclasses import dataclass
from functools import reduce
import math
from typing import Dict, List
from numpy import ndarray, vectorize
import numpy as np

from HyperNeatDomain import LayerShape3D



def sigmoidal(x: float):
    # print(x)
    if (x < -4):
        x = -4
    elif x > 4:
        x = 4

    return 1 / (1 + math.exp(-4.9 * x))


def relu(x: float):
    # print(x)
    if (x < 0):
        x = 0
    if (x > 10000):
        x = 10000

    return x



@dataclass
class ConnectionLocation:
    x1: int
    y1: int
    z1: int
    x2: int
    y2: int
    z2: int
    weight: float


@dataclass
class NetworkBlueprint:
    connections: List[ConnectionLocation]
    id: str
    connectionPlanes: List[LayerShape3D]
    connectionRelationships: Dict[str, List[str]]

# Identify input, hidden and output nodes
def constructNetwork(connections: 'list[ConnectionLocation]',
                     connection_planes: 'list[LayerShape3D]',
                     connection_relationships: 'dict[str, list[str]]',
                     connection_relationships_inverse: 'dict[str, list[str]]',
                     calculation_order: 'list[str]'):

    connection_plane_map : 'dict[str, LayerShape3D]' = dict()
    connection_map : 'dict[str, ndarray]' = dict()
    connection_z_map : 'dict[int, str]'= dict()
    # for r in connection_relationships:
    #     connection_map[p] = np.zeros([p.layerPlane.height, p.layerPlane.width, 2])
    value_map : 'dict[str, ndarray]' = dict()
    for p in connection_planes:
        id = p.layer_plane.id
        value_map[id] = np.zeros([p.layer_plane.height, p.layer_plane.width, 2])
        connection_plane_map[id] = p
        # print(p)
    # print("connection Plane Map:")
    # print(connection_plane_map)
    # print("====")
    for p in connection_planes:
        connection_z_map[p.z_origin] = p.layer_plane.id
        id = p.layer_plane.id
        if p.layer_plane.id in connection_relationships:
            for target_id in connection_relationships[p.layer_plane.id]:
                t = connection_plane_map[target_id]
                # if t.zOrigin == 1 and p.zOrigin == 0:
                #     print("CREATING THE CONNECTION !")
                #     # print(connection_relationships_inverse)
                #     print(connection_relationships_inverse[target_id])
                #     print("==========")
                #     print(connection_z_map)
                #     print("==========")
                #     # print(list(map(lambda d: connection_plane_map[d], connection_relationships_inverse[target_id])))
                #     print(id + ":" + target_id)
                # if id + ":" + target_id in connection_map:
                connection_map[id + ":" + target_id] = np.zeros([
                    t.layer_plane.height, t.layer_plane.width,
                    p.layer_plane.height, p.layer_plane.width,
                ])
    # print(connection_map.keys())
    # print(connection_z_map)
    for c in connections:
        # source = NodeLocation(c.x1, c.y1, c.z1)
        # target = NodeLocation(c.x2, c.y2, c.z2)

        source_id = connection_z_map[c.z1]
        target_id = connection_z_map[c.z2]
        if (source_id + ":" + target_id) in connection_map:
            connection = connection_map[source_id + ":" + target_id]
            connection[c.y2, c.x2, c.y1, c.x1] = c.weight
    print("Constructed Computable Network... " + str(len(connections)))

    return ComputableNetwork(connection_plane_map,
                             connection_relationships_inverse, connection_map,
                             value_map, connection_z_map, calculation_order)


class ComputableNetwork:
    # nodeMap: Dict[NodeLocation, List[Tuple[NodeLocation, ConnectionLocation]]]

    inputNdArray: ndarray
    controller_input: ndarray
    connection_plane_map: 'dict[str, LayerShape3D]'
    connection_relationships_inverse: 'dict[str, list[str]]'
    connection_map: 'dict[str, ndarray]'
    value_map: 'dict[str, ndarray]'
    connection_z_map: 'dict[int, str]'
    calculation_order: 'list[str]'

    def __init__(self, connection_plane_map: 'dict[str, LayerShape3D]',
                 connection_relationships_inverse: 'dict[str, list[str]]',
                 connection_map: 'dict[str, ndarray]', value_map: 'dict[str,ndarray]',
                 connection_z_map: 'dict[int, str]', calculation_order: 'list[str]'):
        self.connection_map = connection_map
        self.connection_plane_map = connection_plane_map
        self.connection_relationships_inverse = connection_relationships_inverse
        self.value_map = value_map
        self.connection_z_map = connection_z_map
        self.calculation_order = calculation_order

    def input(self, input: ndarray):
        self.inputNdArray = input
        self.value_map[self.connection_z_map[0]][..., 0] = self.inputNdArray
        self.value_map[self.connection_z_map[0]][..., 1] = self.inputNdArray
        
    def compute(self):
        vectorizedSigmoidal = np.vectorize(sigmoidal)
        vectorizedRelu = np.vectorize(relu)
        # print(self.connection_map.keys())
        for c in self.calculation_order:
            # if self.connection_plane_map[c].zOrigin == 4:
            #     for s in sources:
            #         print(s + ":" + c)
            # print("new plane: " + str(c))
            if c in self.connection_relationships_inverse:
                sources = self.connection_relationships_inverse[c]
                filtered = filter(lambda s: (s + ":" + c) in self.connection_map, sources)
                # print(len(list(filtered)))
                filteredList = list(filtered)
                # for s in filteredList:
                #     print(self.value_map[s][..., 1].shape)
                #     print(self.connection_map[s + ":" + c].shape)
                # for s in sources:
                #     print(s + ":" + c)
                if len(filteredList) > 0:
                    signal = map(
                        lambda s: (self.value_map[s][..., 1] * self.connection_map[
                            s + ":" + c]).sum((2,3)), filteredList)
                    
                    reduced = reduce(lambda d, d2: d + d2, signal)
                    self.value_map[c][..., 0] = reduced
                    # if (self.connection_plane_map[c].zOrigin < 4 ):
                    #     self.value_map[c][..., 1] = vectorizedRelu(
                    #         reduced)
                    # else:
                    self.value_map[c][..., 1] = vectorizedSigmoidal(reduced)
                    # print(" ===================>\tplane activated")
                    # if self.connection_plane_map[c].zOrigin == 4:
                    #     print(self.value_map[c][..., 1])

    def output(self) -> ndarray:
        # print(self.connection_map[self.connection_z_map[0] + ":" + self.connection_z_map[1]])
        # print(self.value_map[self.connection_z_map[1]][..., 1])
        # print(self.value_map[self.connection_z_map[7]][..., 1])
        return self.value_map[self.connection_z_map[4]][..., 1]
    def outputUnActivated(self) -> ndarray:
        return self.value_map[self.connection_z_map[4]][..., 0]