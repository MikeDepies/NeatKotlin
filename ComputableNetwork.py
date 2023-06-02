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
    adaptive_map: 'dict[str, ndarray]'
    value_map: 'dict[str, ndarray]'
    m_value_map: 'dict[str, ndarray]'
    connection_z_map: 'dict[int, str]'
    calculation_order: 'list[str]'
    total_number_of_connections : float
    total_connection_cost : float

    def __init__(self, connection_plane_map: 'dict[str, LayerShape3D]',
                 connection_relationships_inverse: 'dict[str, list[str]]',
                 connection_map: 'dict[str, ndarray]',adaptive_map: 'dict[str, ndarray]', value_map: 'dict[str,ndarray]', m_value_map: 'dict[str, ndarray]',
                 connection_z_map: 'dict[int, str]', calculation_order: 'list[str]', output_index : 'list[int]',input_index : 'list[int]', activation_function, total_number_of_connections : float, total_connection_cost : float):
        self.connection_map = connection_map
        self.adaptive_map = adaptive_map
        self.connection_plane_map = connection_plane_map
        self.connection_relationships_inverse = connection_relationships_inverse
        self.value_map = value_map
        self.m_value_map = m_value_map
        self.connection_z_map = connection_z_map
        self.calculation_order = calculation_order
        self.output_index = output_index
        self.input_index = input_index
        self.activation_function = activation_function
        self.total_connection_cost = total_connection_cost
        self.total_number_of_connections = total_number_of_connections

    def input(self, input: ndarray):
        self.inputNdArray = input
        self.value_map[self.connection_z_map[0]][..., 0] = self.inputNdArray
        self.value_map[self.connection_z_map[0]][..., 1] = self.inputNdArray
    
    def inputs(self, inputs: 'list[ndarray]'):
        # print(self.input_index)
        for index, input in enumerate(inputs):
            # print(index)
            # print(input.shape)
            
            self.value_map[self.connection_z_map[self.input_index[index]]][..., 0] = input
            self.value_map[self.connection_z_map[self.input_index[index]]][..., 1] = input

    def compute(self):
        vectorized_activation_function = np.vectorize(self.activation_function)
        vectorizedRelu = np.vectorize(relu)
        vectorized_sigmoial = np.vectorize(sigmoidal)
        vectorized_tanh = np.vectorize(math.tanh)
        index = 0
        for c in self.calculation_order:
            if c in self.connection_relationships_inverse:
                sources = self.connection_relationships_inverse[c]
                filtered = filter(lambda s: (s + ":" + c) in self.connection_map, sources)
                filteredList = list(filtered)
                if len(filteredList) > 0:
                    signal = map(
                        lambda s: (self.value_map[s][..., 1] * self.connection_map[
                            s + ":" + c]).sum((2,3)), filteredList)
                    # signal_mod = map(
                    #     lambda s: (self.m_value_map[s] * self.adaptive_map[
                    #         s + ":" + c][...,5]).sum((2,3)), filteredList)
                    
                    reduced = reduce(lambda d, d2: d + d2, signal)
                    # m_reduced : ndarray = reduce(lambda d, d2: d + d2, signal_mod)
                    self.value_map[c][..., 0] = reduced
                    # self.m_value_map[c] = vectorized_tanh(m_reduced / 2)
                    # params = self.adaptive_map[c][...,0:4]
                    # np.array([])
                    # math.tanh

                    if index == len(self.calculation_order) -1:
                        self.value_map[c][..., 1] = vectorized_sigmoial(reduced)
                    else:
                        self.value_map[c][..., 1] = vectorized_activation_function(reduced)
            index +=1

    def output(self) -> 'list[ndarray]':
        # print(self.output_index)
        # print(self.connection_z_map)
        return list(map(lambda index: self.value_map[self.connection_z_map[index]][..., 1], self.output_index))
    def outputUnActivated(self) -> ndarray:
        return self.value_map[self.connection_z_map[self.output_index]][..., 0]

@dataclass
class ComputableNetworkWithID:
    id : str
    network : ComputableNetwork