import enum
from functools import reduce
import json
import math
from collections import defaultdict
from dataclasses import dataclass
from time import sleep
from typing import Dict, List, Set, Tuple

import networkx as nx
import numpy as np
from networkx.algorithms.dag import descendants
from numpy import ndarray, vectorize


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
class LayerPlane:
    height: int
    width: int
    id: str


@dataclass
class LayerShape3D:
    layerPlane: LayerPlane
    xOrigin: int
    yOrigin: int
    zOrigin: int


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
    id: str
    connections: List[ConnectionLocation]
    connection_planes: List[LayerShape3D]
    connection_relationships: Dict[str, List[str]]
    connection_relationships_inverse: dict[str, list[str]]
    calculation_order: list[str]
    output_layer : str


class ConnectionLocation:
    x1: int
    y1: int
    z1: int
    x2: int
    y2: int
    z2: int
    weight: float

    def __init__(self, x1: int, y1: int, z1: int, x2: int, y2: int, z2: int,
                 weight: float):
        self.x1 = x1
        self.y1 = y1
        self.z1 = z1
        self.x2 = x2
        self.y2 = y2
        self.z2 = z2
        self.weight = weight


# Identify input, hidden and output nodes
def constructNetwork(network_blueprint : NetworkBlueprint):
    connections = network_blueprint.connections
    connection_planes = network_blueprint.connection_planes
    connection_relationships = network_blueprint.connection_relationships
    connection_relationships_inverse = network_blueprint.connection_relationships_inverse
    calculation_order = network_blueprint.calculation_order
    output_layer = network_blueprint.output_layer
    connection_plane_map = dict[str, LayerShape3D]()
    connection_map = dict[str, ndarray]()
    connection_z_map = dict[int, str]()
    value_map = dict[str, ndarray]()
    for p in connection_planes:
        id = p.layerPlane.id
        value_map[id] = np.zeros([p.layerPlane.height, p.layerPlane.width, 2])
        connection_plane_map[id] = p
    for p in connection_planes:
        connection_z_map[p.zOrigin] = p.layerPlane.id
        id = p.layerPlane.id
        if p.layerPlane.id in connection_relationships:
            for target_id in connection_relationships[p.layerPlane.id]:
                t = connection_plane_map[target_id]
                connection_map[id + ":" + target_id] = np.zeros([
                    t.layerPlane.height, t.layerPlane.width,
                    p.layerPlane.height, p.layerPlane.width,
                ])
    for c in connections:
        source_id = connection_z_map[c.z1]
        target_id = connection_z_map[c.z2]
        if (source_id + ":" + target_id) in connection_map:
            connection = connection_map[source_id + ":" + target_id]
            connection[c.y2, c.x2, c.y1, c.x1] = c.weight
    print("Constructed Computable Network... " + str(len(connections)))
    output_z_index = connection_plane_map.get(output_layer).zOrigin
    return ComputableNetwork(connection_plane_map,
                             connection_relationships_inverse, connection_map,
                             value_map, connection_z_map, calculation_order, output_z_index)

vectorizedSigmoidal = np.vectorize(sigmoidal)
class ComputableNetwork:
    inputNdArray: ndarray
    controller_input: ndarray
    connection_plane_map: dict[str, LayerShape3D]
    connection_relationships_inverse: dict[str, list[str]]
    connection_map: dict[str, ndarray]
    value_map: dict[str, ndarray]
    connection_z_map: dict[int, str]
    calculation_order: list[str]
    output_layer_index : int
    
    def __init__(self, connection_plane_map: dict[str, LayerShape3D],
                 connection_relationships_inverse: dict[str, list[str]],
                 connection_map: dict[str, ndarray], value_map: dict[str,
                                                                     ndarray],
                 connection_z_map: dict[int,
                                        str], calculation_order: list[str], output_layer_index : int):
        self.connection_map = connection_map
        self.connection_plane_map = connection_plane_map
        self.connection_relationships_inverse = connection_relationships_inverse
        self.value_map = value_map
        self.connection_z_map = connection_z_map
        self.calculation_order = calculation_order
        self.output_layer_index = output_layer_index

    def input(self, input: ndarray, controller_input: ndarray):
        self.inputNdArray = input
        self.controller_input = controller_input 
        self.value_map[self.connection_z_map[0]][..., 0] = self.inputNdArray
        self.value_map[self.connection_z_map[0]][..., 1] = self.inputNdArray
       
    def compute(self):
        for c in self.calculation_order:
            if c in self.connection_relationships_inverse:
                sources = self.connection_relationships_inverse[c]
                filtered = filter(lambda s: (s + ":" + c) in self.connection_map, sources)
                filteredList = list(filtered)
                if len(filteredList) > 0:
                    signal = map(
                        lambda s: (self.value_map[s][..., 1] * self.connection_map[
                            s + ":" + c]).sum((2,3)), filteredList)
                    
                    reduced = reduce(lambda d, d2: d + d2, signal)
                    self.value_map[c][..., 0] = reduced
                    self.value_map[c][..., 1] = vectorizedSigmoidal(reduced)

    def output(self) -> ndarray:
        return self.value_map[self.connection_z_map[self.output_layer_index]][..., 1]


