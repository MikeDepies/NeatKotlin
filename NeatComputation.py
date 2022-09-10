from dataclasses import dataclass
from math import exp, sin
from typing import Dict, Set
from NeatDomain import ConnectionGeneModel, NeatModel, NodeGeneModel

@dataclass
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

def input_nodes(neat_model: NeatModel) -> Set[NodeGeneModel]:
    return set(filter(lambda n: n.node_type == "Input", neat_model.nodes))

def output_nodes(neat_model: NeatModel) -> Set[NodeGeneModel]:
    return set(filter(lambda n: n.node_type == "Output", neat_model.nodes))

def connections_from(nodes : Set[NodeGeneModel], neat_model : NeatModel) -> Set[ConnectionGeneModel]:
    connections = neat_model.connections
    return flat_map(lambda n: filter(lambda c: c.in_node == n.node and c.enabled, connections), nodes)

def node_dict(nodes : Set[NodeGeneModel]) -> Dict[int, NodeGeneModel]:
    d = dict[int, NodeGeneModel]()
    for n in nodes:
        d[n.node] = n
    return d

def create_network_node_map(nodes : Set[NodeGeneModel]) -> Dict[int, NetworkNode]:
    d = dict[int, NetworkNode]()
    for n in nodes:
        d[n.node] = NetworkNode(0, 0, n.activation_function, n.bias)
    return d

def next_nodes(connections : Set[ConnectionGeneModel], node_map : Dict[int, NodeGeneModel]) -> Set[NodeGeneModel]:
    return set(map(lambda c: node_map[c.out_node], connections))



def compute_sequence(neat_model : NeatModel):
    output_nodes = output_nodes(neat_model)
    node_map = node_dict(neat_model.nodes)
    activation_set = set[NodeGeneModel]()
    active_set = set[NodeGeneModel](input_nodes(neat_model))
    network_node_map = create_network_node_map(neat_model.nodes)
    while (len(activation_set) + len(output_nodes)) < len(neat_model.nodes) and len(active_set) > 0:
        captured_set = active_set
        connections = set(captured_set, neat_model)
    