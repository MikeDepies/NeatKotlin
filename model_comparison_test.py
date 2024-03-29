#!/usr/bin/python3
import argparse
import json
import math
import multiprocessing as mp
import time
from dataclasses import dataclass
from typing import Any, List

import numpy as np
from melee.gamestate import GameState, PlayerState, Projectile

from ControllerHelper import ControllerHelper
from Evaluator import Evaluator
from HyperNeatDomain import (HyperDimension3D, LayerPlane, LayerShape3D,
                             NetworkDesign)
from InputEmbeder import InputEmbeder
from ModelHelper import ModelHelper
from NeatDomain import NeatModel, parse_neat_model
from NeatService import NeatService


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



def console_loop(port : int):
    ai_controller_id = 0
    
    model_helper = ModelHelper(ai_controller_id, "localhost")
    neat_service = NeatService("localhost", 8091)
    model_list = model_helper.getModels()
    print(model_list)
    for m in model_list:
        start = time.time()
        id, hyper_neat_builder = neat_service.getNetwork(ai_controller_id, m)
        computable_network = hyper_neat_builder.create_ndarrays()
        print("---------")
        for n in hyper_neat_builder.network_computer.layer_computations:
            print(len(n.nodes))
            print(len(n.weightInstructions))
            # print(len(build_input_nodes(n.weightInstructions)))
            # print(len(build_output_nodes(n.weightInstructions)))
            print("==")
        print("---------")
        # nd_computations = convert_computation_instructions_to_ndarray_instructions_2(hyper_neat_builder.network_computer.layer_computations)
        # nd_neat_builder = HyperNeatBuilder(hyper_neat_builder.network_design, NDNeatComputer(nd_computations), hyper_neat_builder.hyper_shape, hyper_neat_builder.depth, hyper_neat_builder.connection_magnitude)
        # computable_network = nd_neat_builder.create_ndarrays()
        end = time.time()
        print("time: " + str(end - start))
        
    

if __name__ == '__main__':
    processes : List[mp.Process]= []
    for i in range(1):
        p = mp.Process(target=console_loop, args=(i + 51460,), daemon=True)
        processes.append(p)
        p.start()

    for p in processes:
        p.join()