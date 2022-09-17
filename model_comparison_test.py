#!/usr/bin/python3
import argparse
import json
import math
import multiprocessing as mp
from typing import List

import numpy as np
from melee.gamestate import GameState, PlayerState, Projectile

from ComputableNetwork import ConnectionLocation, constructNetwork
from ControllerHelper import ControllerHelper
from Evaluator import Evaluator
from HyperNeatDomain import HyperDimension3D, LayerPlane, LayerShape3D, NetworkDesign
from InputEmbeder import InputEmbeder
from ModelHelper import ModelHelper
from NeatComputation import HyperNeatBuilder, NeatComputer,  create_layer_computation_instructions
from NeatDomain import NeatModel, parse_neat_model


class NumpyEncoder(json.JSONEncoder):
    """ Special json encoder for numpy types """

    def default(self, obj):
        if isinstance(obj, np.integer):
            value = int(obj)
            return 0 if math.isnan(value) else value
        elif isinstance(obj, np.floating):
            value = float(obj)
            if math.isnan(value):
                return 0
            else:
                return value
        elif isinstance(obj, np.ndarray):
            return obj.tolist()
        return json.JSONEncoder.default(self, obj)


def mapC(c):
    return LayerShape3D(
        LayerPlane(c["layerPlane"]["height"],
                                c["layerPlane"]["width"],
                                c["layerPlane"]["id"]), c["xOrigin"],
        c["yOrigin"], c["zOrigin"])



def console_loop(port : int):
    ai_controller_id = 0
    
    model_helper = ModelHelper(ai_controller_id, "localhost")
    model_list = model_helper.getModels()
    print(model_list)
    for m in model_list:
        data = model_helper.getNetworkTest(0, m)
        id: str = data["id"]
        calculation_order = data["calculationOrder"]
        connections: list[ConnectionLocation] = list(
            map(
                lambda c: ConnectionLocation(
                    c[0], c[1], c[2], c[3], c[4], c[5], c[6]),
                data["connections"]))
        connection_relationships: dict[
            str, list[str]] = data["connectionRelationships"]
        connection_relationships_inverse: dict[
            str, list[str]] = data["targetConnectionMapping"]
        connection_planes: list[LayerShape3D] = list(
            map(lambda c: mapC(c), data["connectionPlanes"]))
        neat_model_data = data["neatModel"]
        
        neat_model = parse_neat_model(neat_model_data)
        for n in neat_model.nodes:
            if n.node_type == "Output":
                n.bias = 0
        layer_computation_instructions = create_layer_computation_instructions(neat_model)
        # print(layer_computation_instructions)
        computer = NeatComputer(layer_computation_instructions)
        network_design = NetworkDesign(connection_planes, connection_relationships, connection_relationships_inverse, calculation_order)
        hyper_shape = HyperDimension3D(-1, 1, -1, 1, -1, 1)
        depth = 1
        
        hyper_neat_builder = HyperNeatBuilder(network_design, computer, hyper_shape, depth)
        computer.compute([0,0,0,0,0,0])
        print(computer.output)
        print(neat_model.nodes)
        print(neat_model.connections)
        # python_network = hyper_neat_builder.create_ndarrays()

        # server_network = constructNetwork(connections, connection_planes, connection_relationships, connection_relationships_inverse, calculation_order)
        # for p in network_design.connection_planes:
        #     id = p.layer_plane.id
        #     if p.layer_plane.id in network_design.connection_relationships:
        #         for target_id in network_design.connection_relationships[p.layer_plane.id]:
        #             print(server_network.connection_map[id + ":" + target_id])
        #             print(python_network.connection_map[id + ":" + target_id])
    

if __name__ == '__main__':
    processes : List[mp.Process]= []
    for i in range(1):
        p = mp.Process(target=console_loop, args=(i + 51460,), daemon=True)
        processes.append(p)
        p.start()

    for p in processes:
        p.join()