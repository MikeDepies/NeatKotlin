from typing import Any, Tuple, List
from httpx import post, get
from dataclasses import dataclass
from ComputableNetwork import ComputableNetworkWithID
from HyperNeatDomain import HyperDimension3D, LayerPlane, LayerShape3D, NetworkDesign
from NeatComputation import HyperNeatBuilder, NeatComputer  , create_layer_computation_instructions_2
from NeatDomain import NeatModel, parse_neat_model
import melee
from Configuration import parseStage, parseCharacter
class NeatService:
    host: str
    port: int

    def __init__(self, host : str, port : int) -> None:
        self.host = host
        self.port = port

    def getBestModel(self, controller_id : int) -> NeatModel:
        response = post(self.host + "/stream/next_model", json={
            "controllerId": controller_id
        },)
        data = response.json()
        return parse_neat_model(data)
    
    def getNetwork(self, controllerId, modelId) -> Tuple[str, HyperNeatBuilder]: 
        res = post("http://" + self.host + ":" + str(self.port) + "/model/request", json={
            "controllerId": controllerId,
            "modelId": modelId,
            
        }, timeout=10)
        if not res.is_success:
            raise Exception("No data for request")
        data = res.json()
        
        return process_model_data(data)



def mapC(c):
    return LayerShape3D(
        LayerPlane(c["layerPlane"]["height"],
                                c["layerPlane"]["width"],
                                c["layerPlane"]["id"]), c["xOrigin"],
        c["yOrigin"], c["zOrigin"])

def process_model_data(data : Any) -> Tuple[str, HyperNeatBuilder]:
    id: str = data["id"]
    calculation_order = data["calculationOrder"]
    connection_relationships: dict[
        str, list[str]] = data["connectionRelationships"]
    connection_relationships_inverse: dict[
        str, list[str]] = data["targetConnectionMapping"]
    connection_planes: list[LayerShape3D] = list(
        map(lambda c: mapC(c), data["connectionPlanes"]))
    neat_model_data = data["neatModel"]
    output_layer_str = data["outputLayer"]
    
    neat_model = parse_neat_model(neat_model_data)
    # print(neat_model.nodes[6])
    # print(neat_model.nodes[7])
    input, output, layer_computation_instructions = create_layer_computation_instructions_2(neat_model)
    # print(len(layer_computation_instructions))
    # for instruct in layer_computation_instructions:
    #     print("---")
    #     print(len(instruct.nodes))
    #     print(len(instruct.weightInstructions))
    
    computer = NeatComputer(input, output, layer_computation_instructions)
    network_design = NetworkDesign(connection_planes, connection_relationships, connection_relationships_inverse, calculation_order)
    hyper_shape = HyperDimension3D(-1, 1, -1, 1, -1, 1)
    depth = int(data["depth"])
    
    hyper_neat_builder = HyperNeatBuilder(network_design, computer, hyper_shape, depth, 3, output_layer_str)
    
    return (id, hyper_neat_builder)

@dataclass
class StageGene:
    stage: int
    world : int
    distance: int
    coin: int
    score: int

@dataclass
class StageTrackGene:
    id: str
    stages : List[StageGene]


def process_model_data_mcc(data : Any) -> Tuple[str, HyperNeatBuilder, StageTrackGene]:
    agent_blueprint = data["agent"]
    id: str = agent_blueprint["id"]
    calculation_order = agent_blueprint["calculationOrder"]
    connection_relationships: dict[
        str, list[str]] = agent_blueprint["connectionRelationships"]
    connection_relationships_inverse: dict[
        str, list[str]] = agent_blueprint["targetConnectionMapping"]
    connection_planes: list[LayerShape3D] = list(
        map(lambda c: mapC(c), agent_blueprint["connectionPlanes"]))
    neat_model_data_agent = agent_blueprint["neatModel"]
    neat_model_data_child = data["child"]
    output_layer_str = agent_blueprint["outputLayer"]
    
    neat_model_agent = parse_neat_model(neat_model_data_agent)
    neat_model_child = parse_neat_model(neat_model_data_child)
    # print(neat_model.nodes[6])
    # print(neat_model.nodes[7])
    input, output, layer_computation_instructions = create_layer_computation_instructions_2(neat_model_agent)
    input_child, output_child, layer_computation_instructions_child = create_layer_computation_instructions_2(neat_model_child)
    
    # print(len(layer_computation_instructions))
    # for instruct in layer_computation_instructions:
    #     print("---")
    #     print(len(instruct.nodes))
    #     print(len(instruct.weightInstructions))
    
    computer_agent = NeatComputer(input, output, layer_computation_instructions)
    computer_child = NeatComputer(input_child, output_child, layer_computation_instructions_child)
    
    network_design = NetworkDesign(connection_planes, connection_relationships, connection_relationships_inverse, calculation_order)
    hyper_shape = HyperDimension3D(-1, 1, -1, 1, -1, 1)
    depth = int(agent_blueprint["depth"])
    
    hyper_neat_builder_agent = HyperNeatBuilder(network_design, computer_agent, hyper_shape, depth, 3, output_layer_str)
    hyper_neat_builder_child = HyperNeatBuilder(network_design, computer_child, hyper_shape, depth, 3, output_layer_str)
    
    return (id, hyper_neat_builder_agent, hyper_neat_builder_child)

def process_model_data_mcc_stage(data : Any) -> Tuple[str, HyperNeatBuilder, HyperNeatBuilder]:
    agent_blueprint = data["agent"]
    id: str = agent_blueprint["id"]
    calculation_order = agent_blueprint["calculationOrder"]
    connection_relationships: dict[
        str, list[str]] = agent_blueprint["connectionRelationships"]
    connection_relationships_inverse: dict[
        str, list[str]] = agent_blueprint["targetConnectionMapping"]
    connection_planes: list[LayerShape3D] = list(
        map(lambda c: mapC(c), agent_blueprint["connectionPlanes"]))
    neat_model_data_agent = agent_blueprint["neatModel"]
    environment = data["environment"]
    output_layer_str = agent_blueprint["outputLayer"]
    
    neat_model_agent = parse_neat_model(neat_model_data_agent)
    
    # print(neat_model.nodes[6])
    # print(neat_model.nodes[7])
    input, output, layer_computation_instructions = create_layer_computation_instructions_2(neat_model_agent)
    stage_track_gene = parse_stage_track_gene(environment)
    
    # print(len(layer_computation_instructions))
    # for instruct in layer_computation_instructions:
    #     print("---")
    #     print(len(instruct.nodes))
    #     print(len(instruct.weightInstructions))
    
    computer_agent = NeatComputer(input, output, layer_computation_instructions)
    
    
    network_design = NetworkDesign(connection_planes, connection_relationships, connection_relationships_inverse, calculation_order)
    hyper_shape = HyperDimension3D(-1, 1, -1, 1, -1, 1)
    depth = int(agent_blueprint["depth"])
    
    hyper_neat_builder_agent = HyperNeatBuilder(network_design, computer_agent, hyper_shape, depth, 3, output_layer_str)
    
    
    return (id, hyper_neat_builder_agent, stage_track_gene)

@dataclass
class CPUGene:
    level : int
    kills : int
    deaths: int
    damage: int
    damage_taken: int
    stage: melee.Stage
    character: melee.Character
    cpu_character: melee.Character
    controller_id: int
    ground_movement_distance: float
    unique_actions : int


def process_model_data_mcc_cpu_gene(data : Any) -> Tuple[str, HyperNeatBuilder, CPUGene]:
    agent_blueprint = data["agent"]
    id: str = agent_blueprint["id"]
    calculation_order = agent_blueprint["calculationOrder"]
    connection_relationships: dict[
        str, list[str]] = agent_blueprint["connectionRelationships"]
    connection_relationships_inverse: dict[
        str, list[str]] = agent_blueprint["targetConnectionMapping"]
    connection_planes: list[LayerShape3D] = list(
        map(lambda c: mapC(c), agent_blueprint["connectionPlanes"]))
    neat_model_data_agent = agent_blueprint["neatModel"]
    environment = data["environment"]
    output_layer_str = agent_blueprint["outputLayer"]
    
    neat_model_agent = parse_neat_model(neat_model_data_agent)
    
    # print(neat_model.nodes[6])
    # print(neat_model.nodes[7])
    input, output, layer_computation_instructions = create_layer_computation_instructions_2(neat_model_agent)
    cpu_gene = parse_cpu_gene(environment)
    
    # print(len(layer_computation_instructions))
    # for instruct in layer_computation_instructions:
    #     print("---")
    #     print(len(instruct.nodes))
    #     print(len(instruct.weightInstructions))
    
    computer_agent = NeatComputer(input, output, layer_computation_instructions)
    
    
    network_design = NetworkDesign(connection_planes, connection_relationships, connection_relationships_inverse, calculation_order)
    hyper_shape = HyperDimension3D(-1, 1, -1, 1, -1, 1)
    depth = int(agent_blueprint["depth"])
    
    hyper_neat_builder_agent = HyperNeatBuilder(network_design, computer_agent, hyper_shape, depth, 3, output_layer_str)
    
    
    return (id, hyper_neat_builder_agent, cpu_gene)

def parse_stage_gene(data : Any) -> StageGene:
    return StageGene(int(data["stage"]), int(data["world"]), int(data["distance"]), int(data["coins"]), int(data["score"]))

def parse_stage_track_gene(data : Any) -> StageTrackGene:
    stages = list(map(lambda gene: parse_stage_gene(gene), data["stages"]))
    return StageTrackGene(data["id"], stages)

def parse_cpu_gene(data : Any) -> CPUGene:
    return CPUGene(int(data["level"]), int(data["kills"]), int(data["deaths"]), int(data["damage"]), int(data["damageTaken"]), parseStage(data["stage"]), parseCharacter(data["character"]), parseCharacter(data["cpuCharacter"]), int(data["controllerId"]), float(data["groundMovementDistance"]), int(data["uniqueActions"]))
