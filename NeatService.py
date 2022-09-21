from typing import Any, Tuple
from httpx import post, get
from dataclasses import dataclass
from ComputableNetwork import ComputableNetworkWithID
from HyperNeatDomain import HyperDimension3D, LayerPlane, LayerShape3D, NetworkDesign
from NeatComputation import HyperNeatBuilder, NeatComputer, create_layer_computation_instructions
from NeatDomain import NeatModel, parse_neat_model


class NeatService:
    host: str

    def __init__(self, host : str) -> None:
        self.host = host

    def getBestModel(self, controller_id : int) -> NeatModel:
        response = post(self.host + "/stream/next_model", json={
            "controllerId": controller_id
        },)
        data = response.json()
        return parse_neat_model(data)
    
    def getNetwork(self, controllerId, modelId) -> Tuple[str, HyperNeatBuilder]: 
        res = post("http://" + self.host + ":8091/model/request", json={
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
    
    neat_model = parse_neat_model(neat_model_data)
    layer_computation_instructions = create_layer_computation_instructions(neat_model)
    # print(len(layer_computation_instructions))
    # for instruct in layer_computation_instructions:
    #     print("---")
    #     print(len(instruct.nodes))
    #     print(len(instruct.weightInstructions))
    
    computer = NeatComputer(layer_computation_instructions)
    network_design = NetworkDesign(connection_planes, connection_relationships, connection_relationships_inverse, calculation_order)
    hyper_shape = HyperDimension3D(-1, 1, -1, 1, -1, 1)
    depth = int(data["depth"])
    
    hyper_neat_builder = HyperNeatBuilder(network_design, computer, hyper_shape, depth, 3)
    
    return (id, hyper_neat_builder)