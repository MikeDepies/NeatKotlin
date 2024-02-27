#!/usr/bin/python3
from dataclasses import dataclass
import multiprocessing as mp
import argparse
import json
import math
from multiprocessing.managers import DictProxy, Namespace

import pstats
import cProfile
import random
import signal
import sys
import time
from typing import Any, List
from urllib import response
from httpx import ReadTimeout, get

import melee
import numpy as np
import faulthandler
from melee.gamestate import GameState, PlayerState, Projectile
from ComputableNetwork import ComputableNetwork
from Configuration import Configuration, EvaluatorConfiguration, processConfiguration
from ControllerHelperBinned import ControllerHelper
from Evaluator import Evaluator
from InputEmbeder import InputEmbeder
# from InputEmbederPacked4 import InputEmbederPacked4
from InputEmbederPacked4 import InputEmbederPacked4
from ModelHelper import ModelHelper
from LimitedSizeList import LimitedSizeList

def create_packed_state(gamestate: GameState, player_index: int, opponent_index: int) -> 'list[np.ndarray]':
    positionNormalizer = 100.0
    actionNormalizer = 150.0
    return InputEmbederPacked4(player_index, opponent_index,
                              positionNormalizer, actionNormalizer).embed_input(gamestate)

class ModelHandler:
    network: ComputableNetwork
    evaluator: Evaluator
    ai_controller_id: int
    model_helper: ModelHelper

    model_index: int
    opponent_index: int
    controller: melee.Controller
    controller_helper: ControllerHelper
    queue: mp.Queue
    evaluator_configuration: EvaluatorConfiguration

    def __init__(self, ai_controller_id: int, model_index: int, opponent_index: int, controller: melee.Controller, controller_helper: ControllerHelper, queue: mp.Queue, evaluator_configuration: EvaluatorConfiguration) -> None:
        self.network = None
        self.evaluator = Evaluator(model_index, opponent_index, evaluator_configuration.attack_time,
                                   evaluator_configuration.max_time, evaluator_configuration.action_limit, None)
        self.ai_controller_id = ai_controller_id
        self.model_index = model_index
        self.opponent_index = opponent_index
        self.model_helper = ModelHelper(ai_controller_id, "192.168.0.100")
        self.controller = controller
        self.controller_helper = controller_helper
        self.model_id = ""
        self.queue = queue
        self.evaluator_configuration = evaluator_configuration
        self.stateQueue = LimitedSizeList(10)
        self.bias = np.ones((1,1))

    def evaluate(self, game_state: melee.GameState, delayed_game_state : melee.GameState):
        player0: PlayerState = delayed_game_state.players[self.model_index]
        # player1: PlayerState = game_state.players[self.opponent_index]
        if self.network is not None and self.evaluator is not None:
            
            state = create_packed_state(
                delayed_game_state, self.model_index, self.opponent_index)
            if self.stateQueue.size_limit > 0:
                new_state = state + [self.bias] + self.stateQueue.get_data()
            else:
                new_state = state + [self.bias]
            self.controller_helper.process(
                self.network, self.controller,new_state, player0.controller_state)
            if self.stateQueue.size_limit > 0:
                # self.stateQueue.add(self.network.output()[4])
                self.stateQueue.add(state)
            
            self.evaluator.evaluate_frame(game_state)
            # print("evaluating " + str(self.ai_controller_id))
        elif self.network is None:
            # print("dead " + str(self.ai_controller_id))
            self.controller.release_button(melee.Button.BUTTON_A)
            self.controller.release_button(melee.Button.BUTTON_B)
            if game_state.frame % 3 == 0:
                self.controller.press_button(melee.Button.BUTTON_Y)
            else:
                self.controller.release_button(melee.Button.BUTTON_Y)
            self.controller.release_button(melee.Button.BUTTON_Z)
            self.controller.release_button(melee.Button.BUTTON_L)
            self.controller.press_shoulder(melee.Button.BUTTON_L, 0)
            self.controller.tilt_analog(melee.Button.BUTTON_MAIN, 0, .5)

    def postEvaluate(self, game_state: melee.GameState):
        if self.evaluator.is_finished(game_state) and self.network is not None:
            behavior = self.evaluator.score(game_state)
            # print(behavior.actions)
            self.model_helper.send_evaluation_result(
                self.model_id, behavior)
            # self.reset()
            self.network = None
            self.evaluator = Evaluator(self.model_index, self.opponent_index, self.evaluator_configuration.attack_time,
                                    self.evaluator_configuration.max_time, self.evaluator_configuration.action_limit, None)

            # self.model_id, self.network = self.queue.get()
            # print("creating new evaluator")
            # self.evaluator = Evaluator(self.model_index, self.opponent_index, self.evaluator_configuration.attack_time,
                                    #    self.evaluator_configuration.max_time, self.evaluator_configuration.action_limit, None)
    def reset(self):
        # print("getting network...")
        self.model_id, self.network = self.queue.get()
        # print("Connections:" + str(self.network.total_number_of_connections) + "\tCost: " + str(self.network.total_connection_cost))
        ratio : float = 1
        if self.network.total_connection_cost != 0:
            ratio = self.network.total_number_of_connections/ self.network.total_connection_cost
        # print("creating new evaluator")
        self.stateQueue = LimitedSizeList(len(self.network.input_index)-2) #len(self.network.input_index) - 1
        self.evaluator = Evaluator(self.model_index, self.opponent_index, self.evaluator_configuration.attack_time,
                                   self.evaluator_configuration.max_time, self.evaluator_configuration.action_limit, None)
