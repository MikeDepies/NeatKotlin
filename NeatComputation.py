from dataclasses import dataclass
from math import exp, sin
from typing import Dict, List
from ComputableNetwork import ComputableNetwork
from HyperNeatDomain import HyperDimension3D, LayerShape3D, NetworkDesign
from NeatDomain import ConnectionGeneModel, NeatModel, NodeGeneModel
from numpy import ndarray, vectorize
import numpy as np


@dataclass(unsafe_hash=True)
class NetworkNode:
    value : float
    activated_value: float
    activation_function : str
    bias : float

def sigmoidal(x: float):
    # print(x)
    if (x < -4):
        x = -4
    elif x > 4:
        x = 4

    return 1 / (1 + exp(-4.9 * x))

def linear(x : float):
    if (x > 1):
        return 1
    elif x < -1:
        return -1
    else:
        return x

def gaussian(x : float):
    return exp(-pow(2.5 * x, 2))


def activation_function(activation_fn : str):
    if activation_fn == "identity":
        return lambda v: v
    elif activation_fn == "sigmoidal":
        return sigmoidal
    elif activation_fn == "sine":
        return lambda x: sin(2 * x)
    elif activation_fn == "bipolarGaussian":
        return lambda x: (2 * exp(pow(2.5 * x, 2) * -1)) - 1
    elif activation_fn == "bipolarSigmoid":
        return lambda x: (2 / (1 + exp(-4.9 * x))) - 1
    elif activation_fn == "gaussian":
        return gaussian
    elif activation_fn == "linear":
        return linear
    pass

def activate(network_node: NetworkNode):
    activation_fn = activation_function(network_node.activation_function)
    network_node.value += network_node.bias
    network_node.activated_value = activation_fn(network_node.value)
    network_node.value = 0

def flat_map(f, xs):
    ys = []
    for x in xs:
        ys.extend(f(x))
    return ys

def input_nodes(neat_model: NeatModel) -> List[NodeGeneModel]:
    return list(filter(lambda n: n.node_type == "Input", neat_model.nodes))

def output_nodes(neat_model: NeatModel) -> List[NodeGeneModel]:
    return list(filter(lambda n: n.node_type == "Output", neat_model.nodes))

def connections_from(nodes : List[NodeGeneModel], neat_model : NeatModel) -> List[ConnectionGeneModel]:
    connections = neat_model.connections
    return flat_map(lambda n: filter(lambda c: c.in_node == n.node and c.enabled, connections), nodes)

def node_dict(nodes : List[NodeGeneModel]) -> Dict[int, NodeGeneModel]:
    d = dict[int, NodeGeneModel]()
    for n in nodes:
        d[n.node] = n
    return d

def create_network_node_map(nodes : List[NodeGeneModel]) -> Dict[int, NetworkNode]:
    d = dict[int, NetworkNode]()
    for n in nodes:
        d[n.node] = NetworkNode(0, 0, n.activation_function, n.bias)
    return d

def next_nodes(connections : List[ConnectionGeneModel], node_map : Dict[int, NodeGeneModel]) -> List[NodeGeneModel]:
    return list(map(lambda c: node_map[c.out_node], connections))


@dataclass(unsafe_hash=True)
class WeightComputationInstruction:
    input_node : NetworkNode
    output_node: NetworkNode
    connection_weight : float

    
@dataclass
class LayerComputationInstruction:
    nodes: List[NetworkNode]
    weightInstructions : List[WeightComputationInstruction]

def create_layer_computation_instructions(neat_model : NeatModel) -> List[LayerComputationInstruction]:
    output_nodes_list = output_nodes(neat_model)
    node_map = node_dict(neat_model.nodes)
    activation_set = list[NodeGeneModel]()
    active_set = list[NodeGeneModel](input_nodes(neat_model))
    network_node_map = create_network_node_map(neat_model.nodes)
    layer_computation_instructions = list[LayerComputationInstruction]()
    while (len(activation_set) + len(output_nodes_list)) < len(neat_model.nodes) and len(active_set) > 0:
        captured_set = active_set
        connections = list(connections_from(captured_set, neat_model))
        weight_computation_instruction_set = list(map(lambda c: WeightComputationInstruction(network_node_map[c.in_node], network_node_map[c.out_node], c.weight), connections))
        layer_network_nodes = list(map(lambda n: network_node_map[n.node], captured_set))
        layer_computation_instructions.append(LayerComputationInstruction(layer_network_nodes, weight_computation_instruction_set))
        activation_set.extend(active_set)
        active_set = list(filter(lambda n: n not in activation_set, next_nodes(connections, node_map)))
    output_network_nodes = list(map(lambda n: network_node_map[n.node], output_nodes_list))
    layer_computation_instructions.append(LayerComputationInstruction(output_network_nodes, list()))
    
    return layer_computation_instructions

    
def compute_instructions(layer_computations : List[LayerComputationInstruction], output_nodes : List[NetworkNode], output: List[float]) -> List[float]:
    for layer_computation in layer_computations:
        for node in layer_computation.nodes:
            activate(node)
        for weight_computation in layer_computation.weightInstructions:
            weight_computation.output_node.value = weight_computation.input_node.activated_value + weight_computation.connection_weight
    index = 0
    for x in output_nodes:
        output[index] = x.activated_value
        index+=1
    return output


class NeatComputer:
    layer_computations : List[LayerComputationInstruction]
    output_nodes : List[NetworkNode]
    input_nodes : List[NetworkNode]
    output: List[float]

    def __init__(self, layer_computations : List[LayerComputationInstruction]) -> None:
        self.layer_computations  = layer_computations
        last_index = len(layer_computations) - 1
        self.input_nodes = layer_computations[0].nodes
        self.output_nodes = layer_computations[last_index].nodes
        self.output = list()
        for n in self.output_nodes:
            self.output.append(0)

    def compute(self, input : List[float]):
        index = 0
        for n in self.input_nodes:
            self.input_nodes[index].value = input[index]
            index +=1
        compute_instructions(self.layer_computations, self.output_nodes, self.output)

class HyperNeatBuilder:
    network_design : NetworkDesign
    network_computer : NeatComputer

    def __init__(self, network_design : NetworkDesign, network_computer : NeatComputer, hyper_shape : HyperDimension3D, depth : int) -> None:
        self.network_design = network_design
        self.network_computer = network_computer
        self.hyper_shape = hyper_shape
        self.depth = depth

    def compute_connections_between_layers(self, layer_source : LayerShape3D, layer_target : LayerShape3D):
        source_width = layer_source.layer_plane.width
        source_height = layer_source.layer_plane.height
        source_z = layer_source.z_origin
        target_width = layer_target.layer_plane.width
        target_height = layer_target.layer_plane.height
        target_z = layer_target.z_origin
        connection_ndarray = np.zeros([
                    layer_target.layer_plane.height, layer_target.layer_plane.width,
                    layer_source.layer_plane.height, layer_source.layer_plane.width,
                ])
        total_hyper_x_distance = (self.hyper_shape.x_max - self.hyper_shape.x_min)
        total_hyper_y_distance = (self.hyper_shape.y_max - self.hyper_shape.y_min)
        total_hyper_z_distance = (self.hyper_shape.z_max - self.hyper_shape.z_min)
        source_hyper_z = ((source_z / self.depth) * total_hyper_z_distance) + self.hyper_shape.z_min
        target_hyper_z = ((target_z / self.depth) * total_hyper_z_distance) + self.hyper_shape.z_min
        input = list[float]()
        
        for n in self.network_computer.input_nodes:
            input.append(0)
        input[2] = source_hyper_z
        input[5] = target_hyper_z
        for source_x in range(0, source_width):
            for source_y in range(0, source_height):
                source_hyper_x = ((source_x / source_width) * total_hyper_x_distance) + self.hyper_shape.x_min
                source_hyper_y = ((source_y / source_height) * total_hyper_y_distance) + self.hyper_shape.y_min
                input[0] = source_hyper_x
                input[1] = source_hyper_y
                for target_x in range(0, target_width):
                    for target_y in range(0, target_height):
                        target_hyper_x = ((target_x / target_width) * total_hyper_x_distance) + self.hyper_shape.x_min
                        target_hyper_y = ((target_y / target_height) * total_hyper_y_distance) + self.hyper_shape.y_min
                        input[3] = target_hyper_x
                        input[4] = target_hyper_y
                        self.network_computer.compute(input)
                        weight = self.network_computer.output[0]
                        express_value = self.network_computer.output[1]
                        if (express_value > 0):
                            connection_ndarray[target_y, target_x, source_y, source_x] = weight
        return connection_ndarray
                        

    def create_ndarrays(self) -> ComputableNetwork:
        network_design = self.network_design
        connection_plane_map = dict[str, LayerShape3D]()
        ndarray_map = dict[str, ndarray]()
        connection_map = dict[str, ndarray]()
        connection_zindex_map = dict[int, str]()
        for p in network_design.connection_planes:
            connection_plane_map[p.layer_plane.id] = p
            ndarray_map[p.layer_plane.id] = np.zeros([p.layer_plane.height, p.layer_plane.width, 2])
            connection_zindex_map[p.z_origin] = p.layer_plane.id

        for p in network_design.connection_planes:
            id = p.layer_plane.id
            if p.layer_plane.id in network_design.connection_relationships:
                for target_id in network_design.connection_relationships[p.layer_plane.id]:
                    t : LayerShape3D = connection_plane_map[target_id]
                    connection_map[id + ":" + target_id] = self.compute_connections_between_layers(p, t)
                    # connection_map[id + ":" + target_id] = np.zeros([
                    #     t.layer_plane.height, t.layer_plane.width,
                    #     p.layer_plane.height, p.layer_plane.width,
                    # ])


        return ComputableNetwork(connection_plane_map,
                                network_design.target_connection_mapping, connection_map,
                                ndarray_map, connection_zindex_map, network_design.calculation_order)




    
    


    
    
    