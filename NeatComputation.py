from dataclasses import dataclass
from functools import reduce
from math import exp, sin, sqrt
from typing import Dict, List, Set, Tuple
from ComputableNetwork import ComputableNetwork
from HyperNeatDomain import HyperDimension3D, LayerShape3D, NetworkDesign
from NeatDomain import ConnectionGeneModel, NeatModel, NodeGeneModel
from numpy import ndarray, vectorize
import numpy as np


@dataclass()
class NetworkNode:
    node: int
    value: float
    activated_value: float
    activation_function: str
    bias: float


def sigmoidal(x: float):
    # print(x)
    if (x < -4):
        x = -4
    elif x > 4:
        x = 4

    return 1 / (1 + exp(-4.9 * x))


def linear(x: float):
    if (x > 1):
        return 1
    elif x < -1:
        return -1
    else:
        return x


def gaussian(x: float):
    return exp(-pow(2.5 * x, 2))


def activation_function(activation_fn: str):
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


def connections_from(nodes: List[NodeGeneModel], neat_model: NeatModel) -> List[ConnectionGeneModel]:
    connections = neat_model.connections

    return flat_map(lambda n: filter(lambda c: c.in_node == n.node and c.enabled, connections), nodes)


def connections_to(nodes: List[NodeGeneModel], neat_model: NeatModel) -> List[ConnectionGeneModel]:
    connections = neat_model.connections

    return flat_map(lambda n: filter(lambda c: c.out_node == n.node and c.enabled, connections), nodes)


def node_dict(nodes: List[NodeGeneModel]) -> Dict[int, NodeGeneModel]:
    d : 'Dict[int, NodeGeneModel]'= dict()
    for n in nodes:
        d[n.node] = n
    return d


def create_network_node_map(nodes: List[NodeGeneModel]) -> Dict[int, NetworkNode]:
    d : 'Dict[int, NetworkNode]'= dict()
    for n in nodes:
        d[n.node] = NetworkNode(n.node, 0, 0, n.activation_function, n.bias)
    return d


def next_nodes(connections: List[ConnectionGeneModel], node_map: Dict[int, NodeGeneModel]) -> List[NodeGeneModel]:
    return list(map(lambda c: node_map[c.out_node], connections))


def next_nodes_to(connections: List[ConnectionGeneModel], node_map: Dict[int, NodeGeneModel]) -> List[NodeGeneModel]:
    node_set : 'Set[int]' = set()
    node_list : 'List[NodeGeneModel]'= list()
    for c in connections:
        if c.in_node not in node_set:
            node_set.add(c.in_node)
            node_list.append(node_map[c.in_node])
    return node_list


@dataclass()
class WeightComputationInstruction:
    input_node: NetworkNode
    output_node: NetworkNode
    connection_weight: float


@dataclass
class LayerComputationInstruction:
    nodes: List[NetworkNode]
    weightInstructions: List[WeightComputationInstruction]


def create_node_index_dict(nodes: List[NetworkNode]):
    node_dict : 'Dict[int, int]'= dict()
    index = 0
    for n in nodes:
        node_dict[n.node] = index
        index += 1
    return node_dict


def create_bias_ndarray(nodes: List[NetworkNode]) -> ndarray:
    length = len(nodes)
    bias = np.zeros([1, length])
    bias_index = 0
    for n in nodes:
        bias[0, bias_index] = n.bias
        bias_index += 1
    return bias


def build_input_nodes(weight_instructions: List[WeightComputationInstruction]) -> List[NetworkNode]:
    node_list : 'List[NetworkNode]'= list()
    node_id_set : 'Set[int]' = set()
    for c in weight_instructions:
        if c.input_node.node not in node_id_set:
            node_list.append(c.input_node)
            node_id_set.add(c.input_node.node)
    return node_list


def build_output_nodes(weight_instructions: List[WeightComputationInstruction]) -> List[NetworkNode]:
    node_list : 'List[NetworkNode]'= list()
    node_id_set : 'Set[int]'= set()
    for c in weight_instructions:
        if c.output_node.node not in node_id_set:
            node_list.append(c.output_node)
            node_id_set.add(c.output_node.node)
    return node_list


@dataclass
class NDLayerComputationInstruction:
    nd_nodes: ndarray
    nd_connections: ndarray
    nd_bias: ndarray
    node_dict: 'Dict[int, int]'
    nodes: 'List[NetworkNode]'


def create_ndlayer_computation_instructions(neat_model: NeatModel) -> List[NDLayerComputationInstruction]:
    nd_layer_computation_instructions : 'List[NDLayerComputationInstruction]' = list()
    network_node_map = create_network_node_map(neat_model.nodes)
    output_nodes_list = output_nodes(neat_model)
    node_map = node_dict(neat_model.nodes)
    activation_set : 'List[NodeGeneModel]'= list()
    active_set : 'List[NodeGeneModel]' = list(input_nodes(neat_model))
    # print(network_node_map)
    return nd_layer_computation_instructions


def convert_computation_instructions_to_ndarray_instructions_2(layer_computation_instructions: List[LayerComputationInstruction]) -> List[NDLayerComputationInstruction]:
    # Create nd arrays for each layer of nodes
    # Create connection weight matrix between layers
    # Implement value forwarding for same node id but different ndarray
    nd_layer_computation_instructions : 'List[NDLayerComputationInstruction]'= list()
    # node_map = node_dict(neat_model.nodes)
    for index, layer_instruction in enumerate(layer_computation_instructions):
        output_nodes = build_output_nodes(layer_instruction.weightInstructions)
        input_nodes = build_input_nodes(layer_instruction.weightInstructions)
        if (index == 0):
            input_length = len(input_nodes)
            target_length = len(output_nodes)
            node_index_dict = create_node_index_dict(input_nodes)
            nd_nodes = np.zeros([1, input_length, 2])
            nd_bias = create_bias_ndarray(input_nodes)
            nd_connections = np.zeros([1, input_length])
            # print("index: " + str(index))
            # print(input_length)
            # print(target_length)
            # print("node size: " + str(nd_nodes.shape))
            nd_layer_computation_instructions.append(NDLayerComputationInstruction(
                nd_nodes, nd_connections, nd_bias, node_index_dict, input_nodes))
        elif index > 0:
            prev_layer_computation = layer_computation_instructions[index - 1]
            prev_ndlayer_computation = nd_layer_computation_instructions[index - 1]
            prev_output_nodes = build_output_nodes(
                prev_layer_computation.weightInstructions)
            prev_input_nodes = build_input_nodes(
                prev_layer_computation.weightInstructions)
            input_length = len(prev_input_nodes)
            target_length = len(prev_output_nodes)
            input_node_index_dict = create_node_index_dict(prev_input_nodes)
            output_node_index_dict = create_node_index_dict(prev_output_nodes)
            nd_nodes = np.zeros([1, target_length, 2])
            nd_bias = create_bias_ndarray(prev_output_nodes)
            nd_connections = np.zeros([target_length, input_length])
            # print("index: " + str(index))
            # print(input_length)
            # print(target_length)
            # print("node size: " + str(nd_nodes.shape))
            for c in prev_layer_computation.weightInstructions:
                output_index = output_node_index_dict[c.output_node.node]
                input_index = input_node_index_dict[c.input_node.node]
                nd_connections[output_index, input_index] = c.connection_weight
            nd_layer_computation_instructions.append(NDLayerComputationInstruction(
                nd_nodes, nd_connections, nd_bias, output_node_index_dict, input_nodes))

    return nd_layer_computation_instructions


def convert_computation_instructions_to_ndarray_instructions(layer_computation_instructions: List[LayerComputationInstruction]) -> List[NDLayerComputationInstruction]:
    # Create nd arrays for each layer of nodes
    # Create connection weight matrix between layers
    # Implement value forwarding for same node id but different ndarray
    index = 0
    nd_layer_computation_instructions : 'List[NDLayerComputationInstruction]'= list()
    for layer_instruction in layer_computation_instructions:
        target_length = len(layer_instruction.nodes)
        node_index_dict = create_node_index_dict(layer_instruction.nodes)
        nd_nodes = np.zeros([1, target_length, 2])
        nd_bias = create_bias_ndarray(layer_instruction.nodes)

        nd_connections = np.zeros([target_length, 1])
        print(index)
        print("----- " + str(len(layer_instruction.weightInstructions)))
        if index > 0:
            # need to find the proper prev_layer for each connection...
            # otherwise backwards connections break
            prev_layer_computation = layer_computation_instructions[index - 1]
            source_length = len(prev_layer_computation.nodes)
            nd_connections = np.zeros([target_length, source_length])
            source_node_index_dict = create_node_index_dict(
                prev_layer_computation.nodes)
            for c in prev_layer_computation.weightInstructions:
                # print(c)
                print(str(index - 1) + " => " + str(index))
                print(str(c.input_node.node) + " -> " + str(c.output_node.node))
                input_index = source_node_index_dict[c.input_node.node]
                output_index = node_index_dict[c.output_node.node]
                # print(input_index)
                # print(output_index)
                # print(nd_connections.shape)
                nd_connections[output_index, input_index] = c.connection_weight
        nd_layer_computation_instructions.append(NDLayerComputationInstruction(
            nd_nodes, nd_connections, nd_bias, node_index_dict, layer_instruction.nodes))
        index += 1
    return nd_layer_computation_instructions


def compute_nd_instructions(nd_layer_computations: List[NDLayerComputationInstruction]):
    for index, nd_layer in enumerate(nd_layer_computations):
        # add bias and previous stored values
        # perform activation functions on each node value
        if index > 0:
            prev_nd_layer = nd_layer_computations[index - 1]
            # compute layer values
            print(prev_nd_layer.nd_nodes[..., 1].shape)
            print(nd_layer.nd_connections.shape)
            nd_layer.nd_nodes[0, ..., 0] = (
                prev_nd_layer.nd_nodes[0, ..., 1] * nd_layer.nd_connections).sum((1))  # + nd_layer.nd_bias

            # activate layer
            length = len(nd_layer.nodes)
            for index in range(length):
                activation_fn = activation_function(
                    nd_layer.nodes[index].activation_function)
                nd_layer.nd_nodes[0, index, 1] = activation_fn(
                    nd_layer.nd_nodes[0, index, 0])
                nd_layer.nd_nodes[0, index, 0] = 0




def create_layer_computation_instructions_2(neat_model: NeatModel) -> Tuple[List[NetworkNode], List[NetworkNode], List[LayerComputationInstruction]]:
    output_nodes_list = output_nodes(neat_model)
    node_map = node_dict(neat_model.nodes)
    input_nodes_list = input_nodes(neat_model)
    active_set = output_nodes_list
    network_node_map = create_network_node_map(neat_model.nodes)
    layer_computation_instructions : 'List[LayerComputationInstruction]' = list()
    connections_processed : 'Set[int]'= set()
    
    while len(active_set) > 0:
        captured_set = active_set
        connections = list(filter(
            lambda x: x.innovation not in connections_processed, connections_to(captured_set, neat_model)))
        for c in connections:
            connections_processed.add(c.innovation)
        weight_computation_instruction_set = list(map(lambda c: WeightComputationInstruction(
            network_node_map[c.in_node], network_node_map[c.out_node], c.weight), connections))
        
        layer_network_nodes = list(
            map(lambda n: network_node_map[n.node], captured_set))
        layer_computation_instructions.append(LayerComputationInstruction(
            layer_network_nodes, weight_computation_instruction_set))
        
        active_set = list(
            next_nodes_to(connections, node_map))
    input_network_nodes = list(
        map(lambda n: network_node_map[n.node], input_nodes_list))
    output_network_nodes = list(
        map(lambda n: network_node_map[n.node], output_nodes_list))
    
    nodes_visited : 'Set[int]'= set()
    for lci in layer_computation_instructions:
        nodes_to_remove : 'List[NodeGeneModel]'= list()
        for n in lci.nodes:
            if n.node not in nodes_visited:
                nodes_visited.add(n.node)
            else:
                nodes_to_remove.append(n)
        for n in nodes_to_remove:
            lci.nodes.remove(n)
    node_count = reduce(
        lambda a, b: a + b, map(lambda l: len(l.nodes), layer_computation_instructions), 0)
    # print("Nodes after prune: " + str(node_count))
    # print(network_node_map)
    layer_computation_instructions.reverse()
    return (input_network_nodes, output_network_nodes, layer_computation_instructions)


def compute_instructions(layer_computations: List[LayerComputationInstruction], output_nodes: List[NetworkNode], output: List[float]) -> List[float]:
    for layer_computation in layer_computations:
        for weight_computation in layer_computation.weightInstructions:
            weight_computation.output_node.value += weight_computation.input_node.activated_value * \
                weight_computation.connection_weight
        for node in layer_computation.nodes:
            activate(node)
    index = 0
    for x in output_nodes:
        output[index] = x.activated_value
        index += 1
    return output


class NeatComputer:
    layer_computations: List[LayerComputationInstruction]
    output_nodes: List[NetworkNode]
    input_nodes: List[NetworkNode]
    output: List[float]

    def __init__(self, input_nodes: List[NetworkNode], output_nodes: List[NetworkNode], layer_computations: List[LayerComputationInstruction]) -> None:
        self.layer_computations = layer_computations
        self.input_nodes = input_nodes
        self.output_nodes = output_nodes
        self.output = list()
        for n in self.output_nodes:
            self.output.append(0)

    def compute(self, input: List[float]):
        index = 0
        for n in self.input_nodes:
            self.input_nodes[index].value = input[index]
            self.input_nodes[index].activated_value = input[index]
            index += 1

        compute_instructions(self.layer_computations,
                             self.output_nodes, self.output)

    def output_values(self) -> List[float]:
        return self.output


class NDNeatComputer:
    layer_computations: List[NDLayerComputationInstruction]

    input_nodes: ndarray
    output_nodes: ndarray

    def __init__(self, layer_computations: List[NDLayerComputationInstruction]) -> None:
        self.layer_computations = layer_computations
        last_index = len(layer_computations) - 1
        self.input_nodes = layer_computations[0].nd_nodes
        self.output_nodes = layer_computations[last_index].nd_nodes
        # ensure that these references hold through assignment...?

    def compute(self, input: List[float]):
        self.input_nodes[0, ..., 0].put(0, input)
        self.input_nodes[0, ..., 0].put(0, input)
        compute_nd_instructions(self.layer_computations)

    def output_values(self) -> List[float]:
        return self.output_nodes[0, ..., 1].tolist()


class HyperNeatBuilder:
    network_design: NetworkDesign
    network_computer: NeatComputer

    def __init__(self, network_design: NetworkDesign, network_computer: NeatComputer, hyper_shape: HyperDimension3D, depth: int, connection_magnitude: float, output_layer : 'list[str]', input_layer : 'list[str]') -> None:
        self.network_design = network_design
        self.network_computer = network_computer
        self.hyper_shape = hyper_shape
        self.depth = depth
        self.connection_magnitude = connection_magnitude
        self.output_layer = output_layer
        self.input_layer = input_layer

    def compute_connections_between_layers(self, layer_source: LayerShape3D, layer_target: LayerShape3D):
        source_width = layer_source.layer_plane.width
        source_height = layer_source.layer_plane.height
        source_z = layer_source.z_origin
        source_x_origin = layer_source.x_origin
        source_y_origin = layer_source.y_origin
        target_width = layer_target.layer_plane.width
        target_height = layer_target.layer_plane.height
        target_z = layer_target.z_origin
        target_x_origin = layer_target.x_origin
        target_y_origin = layer_target.y_origin
        connection_ndarray = np.zeros([
            layer_target.layer_plane.height, layer_target.layer_plane.width,
            layer_source.layer_plane.height, layer_source.layer_plane.width,
        ])
        total_hyper_x_distance = (
            self.hyper_shape.x_max - self.hyper_shape.x_min)
        total_hyper_y_distance = (
            self.hyper_shape.y_max - self.hyper_shape.y_min)
        total_hyper_z_distance = (
            self.hyper_shape.z_max - self.hyper_shape.z_min)
        source_hyper_z = ((source_z / self.depth) *
                          total_hyper_z_distance) + self.hyper_shape.z_min
        target_hyper_z = ((target_z / self.depth) *
                          total_hyper_z_distance) + self.hyper_shape.z_min
        input : 'List[float]'= list()

        for n in range(7):
            input.append(0)
        input[2] = source_hyper_z
        input[5] = target_hyper_z
        for source_x in range(0, source_width):
            for source_y in range(0, source_height):
                source_hyper_x = ((source_x / max(source_width - 1, 1))
                                  * total_hyper_x_distance) + self.hyper_shape.x_min
                source_hyper_y = ((source_y / max(source_height - 1, 1))
                                  * total_hyper_y_distance) + self.hyper_shape.y_min
                input[0] = source_hyper_x + source_x_origin
                input[1] = source_hyper_y + source_y_origin
                for target_x in range(0, target_width):
                    for target_y in range(0, target_height):
                        target_hyper_x = ((target_x / max(target_width - 1, 1))
                                          * total_hyper_x_distance) + self.hyper_shape.x_min
                        target_hyper_y = ((target_y / max(target_height - 1, 1))
                                          * total_hyper_y_distance) + self.hyper_shape.y_min
                        input[3] = target_hyper_x + target_x_origin
                        input[4] = target_hyper_y + target_y_origin
                        # Length of connection
                        input[6] = sqrt(pow(input[0] - input[3], 2) + pow(input[1] - input[4], 2) + pow(input[2] - input[5], 2))
                        self.network_computer.compute(input)
                        # print(input)
                        # print(self.network_computer.output_values())
                        output_values = self.network_computer.output_values()
                        weight = output_values[0]
                        express_value = output_values[1]
                        if (express_value > 0):
                            connection_ndarray[target_y, target_x, source_y,
                                               source_x] = weight * self.connection_magnitude
        return connection_ndarray

    def filter_ndarrays(self, target_id : str, connection_plane_map : 'Dict[str, LayerShape3D]', connection_map : 'Dict[str, ndarray]'):
        network_design = self.network_design
        for source_id in network_design.target_connection_mapping[target_id]:
            connections = connection_map[source_id + ":" + target_id]
            # target y, x source y, x
            
            pass
        pass

    def create_ndarrays(self, activation_function) -> ComputableNetwork:
        network_design = self.network_design
        connection_plane_map : 'Dict[str, LayerShape3D]'= dict()
        ndarray_map : 'Dict[str, ndarray]'= dict()
        connection_map : 'Dict[str, ndarray]'= dict()
        connection_zindex_map : 'Dict[int, str]'= dict()
        zindex_map : 'Dict[str, int]'= dict()
        for p in network_design.connection_planes:
            connection_plane_map[p.layer_plane.id] = p
            ndarray_map[p.layer_plane.id] = np.zeros(
                [p.layer_plane.height, p.layer_plane.width, 2])
            # print( str(p.z_origin) +" - " + str(ndarray_map[p.layer_plane.id].shape))
            connection_zindex_map[p.z_origin] = p.layer_plane.id
            zindex_map[p.layer_plane.id] = p.z_origin
        connection_count = 0
        for p in network_design.connection_planes:
            id = p.layer_plane.id
            if p.layer_plane.id in network_design.connection_relationships:
                for target_id in network_design.connection_relationships[p.layer_plane.id]:
                    t: LayerShape3D = connection_plane_map[target_id]
                    connection_map[id + ":" +
                                   target_id] = self.compute_connections_between_layers(p, t)
                    connection_count += connection_map[id +
                                                       ":" + target_id].size
        output_index = list(map(lambda layer: zindex_map.get(layer), self.output_layer))
        input_index = list(map(lambda layer: zindex_map.get(layer), self.input_layer))
        #Need to create an inverted connection_zindex map and use that instead of calculation order to find indexes for output and input
        return ComputableNetwork(connection_plane_map,
                                 network_design.target_connection_mapping, connection_map,
                                 ndarray_map, connection_zindex_map, network_design.calculation_order, output_index, input_index, activation_function)
