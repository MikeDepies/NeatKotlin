#!/usr/bin/python3

import melee
import numpy as np
from melee.gamestate import GameState, PlayerState
from ComputableNetwork import ComputableNetwork
from Configuration import Configuration, EvaluatorConfiguration
from ControllerHelperBinned import ControllerHelper
from EvaluatorCpuMCC import EvaluatorCpuMCC
# from InputEmbederPacked import InputEmbederPacked
from InputEmbederPacked3 import InputEmbederPacked3


def create_packed_state(gamestate: GameState, player_index: int, opponent_index: int) -> np.ndarray:
    positionNormalizer = 30.0
    actionNormalizer = 150.0
    return InputEmbederPacked3(player_index, opponent_index,
                              positionNormalizer, actionNormalizer).embed_input(gamestate)

class ModelHandlerMCC_CPU:
    network: ComputableNetwork
    evaluator: EvaluatorCpuMCC
    ai_controller_id: int

    model_index: int
    opponent_index: int
    controller: melee.Controller
    controller_helper: ControllerHelper
    evaluator_configuration: EvaluatorConfiguration

    def __init__(self, ai_controller_id: int, model_index: int, opponent_index: int, controller: melee.Controller, controller_helper: ControllerHelper, evaluator_configuration: EvaluatorConfiguration) -> None:
        self.network = None
        self.evaluator = EvaluatorCpuMCC(model_index, opponent_index, None)
        self.ai_controller_id = ai_controller_id
        self.model_index = model_index
        self.opponent_index = opponent_index
        self.controller = controller
        self.controller_helper = controller_helper
        self.evaluator_configuration = evaluator_configuration

    def evaluate(self, game_state: melee.GameState):
        player0: PlayerState = game_state.players[self.model_index]
        player1: PlayerState = game_state.players[self.opponent_index]
        if self.network is not None and self.evaluator is not None:
            state = create_packed_state(
                game_state, self.model_index, self.opponent_index)
            
            self.controller_helper.process(
                self.network, self.controller, state, player0.controller_state)
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

    def reset(self, network : ComputableNetwork):
        self.network = network
        self.evaluator = EvaluatorCpuMCC(self.model_index, self.opponent_index, None)
