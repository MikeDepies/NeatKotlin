from dataclasses import dataclass
from functools import reduce
from math import exp, sin, sqrt, cos
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


def sigmoidal(x: float) -> float:
    """
    Sigmoidal activation function.
    :param x: Input to the function.
    :return: Output after applying sigmoidal function.
    """
    x = np.clip(x, -4, 4)
    return 1 / (1 + np.exp(-4.9 * x))


def linear(x: float) -> float:
    """
    Linear activation function with clipping.
    :param x: Input to the function.
    :return: Output after applying linear function.
    """
    return np.clip(x, -1, 1)


def gaussian(x: float) -> float:
    """
    Gaussian activation function.
    :param x: Input to the function.
    :return: Output after applying gaussian function.
    """
    return np.exp(-np.power(2.5 * x, 2))


def activation_function(activation_fn: str):
    """
    Map activation function names to corresponding function definitions.
    :param activation_fn: Activation function name.
    :return: Corresponding activation function.
    """
    activation_dict = {
        "identity": lambda v: v,
        "sigmoidal": sigmoidal,
        "sine": lambda x: sin( x),
        "cos": lambda x: cos( x),
        "bipolarGaussian": lambda x: (2 * np.exp(np.power(2.5 * x, 2) * -1)) - 1,
        "bipolarSigmoid": lambda x: (2 / (1 + np.exp(-4.9 * x))) - 1,
        "gaussian": gaussian,
        "linear": linear,
        "abs": abs,
    }
    
    if activation_fn in activation_dict:
        return activation_dict[activation_fn]
    
    raise ValueError(f"Invalid activation function: {activation_fn}")

from typing import List, Dict, Tuple, Set, Any
from functools import reduce

def activate(network_node: NetworkNode):
    """Apply the activation function to the node and reset its value."""
    activation_fn = activation_function(network_node.activation_function)
    network_node.value += network_node.bias
    network_node.activated_value = activation_fn(network_node.value)
    network_node.value = 0


def flat_map(f, xs: List[Any]) -> List[Any]:
    """Flatten a list of lists using a provided function."""
    return [item for sublist in xs for item in f(sublist)]


def input_nodes(neat_model: NeatModel) -> List[NodeGeneModel]:
    """Get the list of input nodes from the NEAT model."""
    return [n for n in neat_model.nodes if n.node_type == "Input"]


def output_nodes(neat_model: NeatModel) -> List[NodeGeneModel]:
    """Get the list of output nodes from the NEAT model."""
    return [n for n in neat_model.nodes if n.node_type == "Output"]


def connections_to(nodes: List[NodeGeneModel], neat_model: NeatModel) -> List[ConnectionGeneModel]:
    """Get the list of connections to the provided nodes in the NEAT model."""
    connections = neat_model.connections
    return flat_map(lambda n: [c for c in connections if c.out_node == n.node and c.enabled], nodes)


def node_dict(nodes: List[NodeGeneModel]) -> Dict[int, NodeGeneModel]:
    """Create a dictionary mapping node IDs to nodes."""
    return {n.node: n for n in nodes}


def create_network_node_map(nodes: List[NodeGeneModel]) -> Dict[int, NetworkNode]:
    """Create a dictionary mapping node IDs to network nodes."""
    return {n.node: NetworkNode(n.node, 0, 0, n.activation_function, n.bias) for n in nodes}


def next_nodes_to(connections: List[ConnectionGeneModel], node_map: Dict[int, NodeGeneModel]) -> List[NodeGeneModel]:
    """Get the list of next nodes connected to the provided connections."""
    node_set = {c.in_node for c in connections}
    return [node_map[node_id] for node_id in node_set]


@dataclass()
class WeightComputationInstruction:
    input_node: NetworkNode
    output_node: NetworkNode
    connection_weight: float


@dataclass
class LayerComputationInstruction:
    nodes: List[NetworkNode]
    weightInstructions: List[WeightComputationInstruction]

def create_layer_computation_instructions_2(neat_model: NeatModel) -> Tuple[List[NetworkNode], List[NetworkNode], List[LayerComputationInstruction]]:
    """
    Create instructions for computing layers.
    
    Args:
        neat_model (NeatModel): NEAT model to process.

    Returns:
        Tuple containing the list of input network nodes, output network nodes, and layer computation instructions.
    """
    output_nodes_list = output_nodes(neat_model)
    node_map = node_dict(neat_model.nodes)
    input_nodes_list = input_nodes(neat_model)
    active_set = output_nodes_list
    network_node_map = create_network_node_map(neat_model.nodes)
    layer_computation_instructions: List[LayerComputationInstruction] = []
    connections_processed: Set[int] = set()

    while active_set:
        captured_set = active_set
        connections = [x for x in connections_to(captured_set, neat_model) if x.innovation not in connections_processed]
        connections_processed.update([c.innovation for c in connections])
        weight_computation_instruction_set = [WeightComputationInstruction(network_node_map[c.in_node], network_node_map[c.out_node], c.weight) for c in connections]

        layer_network_nodes = [network_node_map[n.node] for n in captured_set]
        layer_computation_instructions.append(LayerComputationInstruction(layer_network_nodes, weight_computation_instruction_set))

        active_set = next_nodes_to(connections, node_map)

    input_network_nodes = [network_node_map[n.node] for n in input_nodes_list]
    output_network_nodes = [network_node_map[n.node] for n in output_nodes_list]

    nodes_visited: Set[int] = set()
    for lci in layer_computation_instructions:
        nodes_to_remove = [n for n in lci.nodes if n.node in nodes_visited]
        nodes_visited.update([n.node for n in lci.nodes])
        for n in nodes_to_remove:
            lci.nodes.remove(n)
    layer_computation_instructions.reverse()
    return (input_network_nodes, output_network_nodes, layer_computation_instructions)


def compute_instructions(layer_computations: List[LayerComputationInstruction], output_nodes: List[NetworkNode], output: List[float]) -> List[float]:
    """
    Compute the layer instructions.
    
    Args:
        layer_computations (List[LayerComputationInstruction]): Instructions to compute layers.
        output_nodes (List[NetworkNode]): List of output nodes.
        output (List[float]): List to store output values.

    Returns:
        List of output values after computation.
    """
    for layer_computation in layer_computations:
        for weight_computation in layer_computation.weightInstructions:
            weight_computation.output_node.value += weight_computation.input_node.activated_value * weight_computation.connection_weight
        for node in layer_computation.nodes:
            activate(node)

    for i, x in enumerate(output_nodes):
        output[i] = x.activated_value
    return output


class NeatComputer:
    """A class that represents the NEAT computation."""

    def __init__(self, input_nodes: List[NetworkNode], output_nodes: List[NetworkNode], layer_computations: List[LayerComputationInstruction]):
        self.layer_computations = layer_computations
        self.input_nodes = input_nodes
        self.output_nodes = output_nodes
        self.output = [0] * len(self.output_nodes)

    def compute(self, input: List[float]):
        """Perform the computation for the NEAT model."""
        for index, value in enumerate(input):
            self.input_nodes[index].value = value
            self.input_nodes[index].activated_value = value
        compute_instructions(self.layer_computations, self.output_nodes, self.output)

    def output_values(self) -> List[float]:
        """Return the output values from the NEAT computation."""
        return self.output




class HyperNeatBuilder:
    network_design: NetworkDesign
    network_computer: NeatComputer

    def __init__(self, network_design: NetworkDesign, network_computer: NeatComputer, hyper_shape: HyperDimension3D, depth: int, connection_magnitude: float, output_layer: 'list[str]', input_layer: 'list[str]') -> None:
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
        adaptive_ndarray = np.zeros([
            layer_target.layer_plane.height, layer_target.layer_plane.width,
            layer_source.layer_plane.height, layer_source.layer_plane.width,
            6
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
        input: 'List[float]' = list()
        connection_cost_sum = 0
        connections_expressed = 0
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
                        length = sqrt(pow(
                            input[0] - input[3], 2) + pow(input[1] - input[4], 2) + pow(input[2] - input[5], 2))
                        input[6] = length
                        self.network_computer.compute(input)
                        # print(input)
                        # print(self.network_computer.output_values())
                        output_values = self.network_computer.output_values()
                        weight = output_values[0]
                        if (len(output_values) > 1):
                            express_value = output_values[1]
                            if (express_value > 0):
                                if weight != 0:
                                    l1 = abs(input[0] - input[3]) + abs(input[1] - input[4]) + abs(input[2] - input[5])
                                    connections_expressed += 1
                                    connection_cost_sum += max(0, l1 - 3)
                                # adaptive_ndarray[target_y, target_x, source_y,
                                #                    source_x, ...] = output_values[2:]
                                connection_ndarray[target_y, target_x, source_y,
                                                source_x] = weight * self.connection_magnitude
                        else:
                            threshold = .3
                            if abs(weight) > threshold:
                                if weight > 0:
                                    normalized_weight = (weight - threshold) / (1-threshold)
                                else:
                                    normalized_weight = (weight + threshold) / (1-threshold)
                                
                                connection_ndarray[target_y, target_x, source_y,
                                                source_x] = normalized_weight * self.connection_magnitude

        return (connection_ndarray, adaptive_ndarray, connections_expressed, connection_cost_sum)

    def filter_ndarrays(self, target_id: str, connection_plane_map: 'Dict[str, LayerShape3D]', connection_map: 'Dict[str, ndarray]'):
        network_design = self.network_design
        for source_id in network_design.target_connection_mapping[target_id]:
            connections = connection_map[source_id + ":" + target_id]
            # target y, x source y, x

            pass
        pass

    def create_ndarrays(self, activation_function, output_activation_function) -> ComputableNetwork:
        network_design = self.network_design
        connection_plane_map: 'Dict[str, LayerShape3D]' = dict()
        ndarray_map: 'Dict[str, ndarray]' = dict()
        m_ndarray_map: 'Dict[str, ndarray]' = dict()
        connection_map: 'Dict[str, ndarray]' = dict()
        adaptive_map: 'Dict[str, ndarray]' = dict()
        connection_zindex_map: 'Dict[int, str]' = dict()
        zindex_map: 'Dict[str, int]' = dict()
        total_number_of_connections = 0
        total_connection_cost = 0
        for p in network_design.connection_planes:
            connection_plane_map[p.layer_plane.id] = p
            ndarray_map[p.layer_plane.id] = np.zeros(
                [p.layer_plane.height, p.layer_plane.width, 2])
            m_ndarray_map[p.layer_plane.id] = np.zeros(
                [p.layer_plane.height, p.layer_plane.width])
            # print( str(p.z_origin) +" - " + str(ndarray_map[p.layer_plane.id].shape))
            connection_zindex_map[p.z_origin] = p.layer_plane.id
            zindex_map[p.layer_plane.id] = p.z_origin
        connection_count = 0
        for p in network_design.connection_planes:
            id = p.layer_plane.id
            if p.layer_plane.id in network_design.connection_relationships:
                for target_id in network_design.connection_relationships[p.layer_plane.id]:
                    t: LayerShape3D = connection_plane_map[target_id]
                    connection_ndarray, adaptive_ndarray, connections_expressed, connection_cost_sum = self.compute_connections_between_layers(
                        p, t)
                    total_connection_cost += connection_cost_sum
                    total_number_of_connections += connections_expressed
                    connection_map[id + ":" +
                                   target_id] = connection_ndarray
                    adaptive_map[id + ":" +
                                 target_id] = adaptive_ndarray
                    connection_count += connection_ndarray[connection_ndarray != 0].size
        output_index = list(
            map(lambda layer: zindex_map.get(layer), self.output_layer))
        input_index = list(
            map(lambda layer: zindex_map.get(layer), self.input_layer))
        # Need to create an inverted connection_zindex map and use that instead of calculation order to find indexes for output and input
        print("Size of network: " + str(connection_count))
        print(f'input layers: {len(self.input_layer)}')
     
        
        if total_connection_cost <= 0:
            total_connection_cost = 1
        return ComputableNetwork(connection_plane_map,
                                 network_design.target_connection_mapping, connection_map, adaptive_map,
                                 ndarray_map, m_ndarray_map, connection_zindex_map, network_design.calculation_order, output_index, input_index, activation_function, output_activation_function, total_number_of_connections, total_connection_cost)

