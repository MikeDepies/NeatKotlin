from dataclasses import dataclass
from typing import Any, List


@dataclass()
class NodeGeneModel:
    node : int
    bias: float
    node_type : str
    activation_function: str

@dataclass()
class ConnectionGeneModel:
    in_node: int
    out_node: int
    weight: float
    enabled: bool
    innovation: int

    
@dataclass
class NeatModel:
    nodes: List[NodeGeneModel]
    connections: List[ConnectionGeneModel]

@dataclass
class ModelMeta:
    neat_model : NeatModel
    id: str


def parse_node(data: Any) -> NodeGeneModel:
    return NodeGeneModel(data["node"], data["bias"], data["nodeType"], data["activationFunction"])

def parse_nodes(data : List[Any]) -> List[NodeGeneModel]:
    return list(map(parse_node, data))

def parse_connection(data: Any) -> ConnectionGeneModel:
    return ConnectionGeneModel(data["inNode"], data["outNode"], data["weight"], data["enabled"], data["innovation"])

def parse_connections(data: List[Any]) -> List[ConnectionGeneModel]:
    return list(map(parse_connection, data))


def parse_neat_model(data : Any):
    nodes : List[Any] = data["nodes"]
    connections : List[Any] = data["connections"]
    return NeatModel(parse_nodes(nodes), parse_connections(connections))

def parse_model_meta(data: Any) -> ModelMeta:
    neat_model = parse_neat_model(data["neatModel"])
    id = data["id"]
    return ModelMeta(neat_model, id)