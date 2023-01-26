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
                 connection_z_map: 'dict[int, str]', calculation_order: 'list[str]', output_index : 'list[int]', activation_function):
        self.connection_map = connection_map
        self.connection_plane_map = connection_plane_map
        self.connection_relationships_inverse = connection_relationships_inverse
        self.value_map = value_map
        self.connection_z_map = connection_z_map
        self.calculation_order = calculation_order
        self.output_index = output_index
        self.activation_function = activation_function

    def input(self, input: ndarray):
        self.inputNdArray = input
        self.value_map[self.connection_z_map[0]][..., 0] = self.inputNdArray
        self.value_map[self.connection_z_map[0]][..., 1] = self.inputNdArray
        
    def compute(self):
        vectorized_activation_function = np.vectorize(self.activation_function)
        vectorizedRelu = np.vectorize(relu)
        vectorized_sigmoial = np.vectorize(sigmoidal)
        # print(self.connection_map.keys())
        index = 0
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
                    if index == len(self.calculation_order) -1:
                        self.value_map[c][..., 1] = vectorized_sigmoial(reduced)
                    else:
                        self.value_map[c][..., 1] = vectorized_activation_function(reduced)
                    # print(" ===================>\tplane activated")
                    # if self.connection_plane_map[c].zOrigin == 4:
                    #     print(self.value_map[c][..., 1])
            index +=1

    def output(self) -> 'list[ndarray]':
        
        return list(map(lambda index: self.value_map[self.connection_z_map[index+1]][..., 1], self.output_index))
    def outputUnActivated(self) -> ndarray:
        return self.value_map[self.connection_z_map[self.output_index+1]][..., 0]

@dataclass
class ComputableNetworkWithID:
    id : str
    network : ComputableNetwork