#!/usr/bin/python3

import multiprocessing as mp

from multiprocessing.managers import DictProxy, Namespace
import random
import time
from typing import Any, List, Tuple
from urllib import response
from httpx import ReadTimeout, get
import random
import melee
from melee.gamestate import GameState, PlayerState, Projectile
from ComputableNetwork import ComputableNetwork, sigmoidal
from Configuration import Configuration, EvaluatorConfiguration, processConfiguration
from ControllerHelperBinned import ControllerHelper

from ModelHelperMCC_CPUGene import ModelHelperMCC_CPUGene, EvalResultCPU
from ModelHandlerCPUGene import ModelHandlerMCC_CPU
from ModelHelper import ModelHelper
from meleeConsole import startConsole
from NeatComputation import HyperNeatBuilder
from NeatService import CPUGene
from dataclasses import dataclass
from cpuSelector import choose_character, menu_helper_simple


@dataclass
class ControllerDef:
    level: int
    character: melee.Character
    controller: melee.Controller
    player_index: int


def get_next(queue: mp.Queue) -> Tuple[str, HyperNeatBuilder, CPUGene]:
    return queue.get()


def console_loop_mcc_cpu_gene(queue_1: mp.Queue, configuration: Configuration, port : int):
    console: melee.Console
    controller: melee.Controller
    controller_opponent: melee.Controller
    console, controller, controller_opponent, args, log = startConsole(port)
    player_index = args.port
    opponent_index = args.opponent

    host = "192.168.0.100"
    model_helper = ModelHelperMCC_CPUGene(host)
    controller_helper = ControllerHelper()
    id, agent, cpu_gene = get_next(queue_1)
    aiDef = aiControllerDef(cpu_gene, controller,
                            controller_opponent, player_index, opponent_index)
    cpuDef = opponentControllerDef(
        cpu_gene, controller, controller_opponent, player_index, opponent_index)
    model_handler = ModelHandlerMCC_CPU(cpu_gene.controller_id, aiDef.player_index, cpuDef.player_index,
                                        aiDef.controller, controller_helper, configuration.evaluator)
    print(cpu_gene)
    characters = [melee.Character.FOX, melee.Character.CPTFALCON, melee.Character.YOSHI, melee.Character.FALCO, melee.Character.DOC, melee.Character.DK, melee.Character.PIKACHU, melee.Character.LINK, melee.Character.MARIO, melee.Character.MARTH, melee.Character.BOWSER,
                  melee.Character.GAMEANDWATCH, melee.Character.GANONDORF, melee.Character.JIGGLYPUFF, melee.Character.KIRBY, melee.Character.LUIGI, melee.Character.MEWTWO, melee.Character.NESS, melee.Character.PEACH, melee.Character.PICHU, melee.Character.ROY, melee.Character.ZELDA, melee.Character.YLINK]
    stages = [melee.Stage.FINAL_DESTINATION,
              melee.Stage.BATTLEFIELD, melee.Stage.POKEMON_STADIUM]
    # Figure out which handlers to pass the networks to
    model_handler.reset(agent)
    check_controller_status = False
    ai_controller_index = 0
    cpu_level = cpu_gene.level
    while True:
        game_state = console.step()
        if game_state is None:
            print("We hit this None BS")
            continue

        if game_state.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:
            check_controller_status = True
            player0: PlayerState = game_state.players[player_index]
            player1: PlayerState = game_state.players[opponent_index]
            model_handler.evaluate(game_state)
            # score = model_handler.evaluator.score()
            # if (score.total_frames_alive % 60 * 20 == 0):
            # print(score)
            # if not (model_handler.network == None):
            #     if (score.deaths >= cpu_gene.deaths or score.total_damage_taken >= cpu_gene.damage_taken or score.total_frames_alive / 60 > max(1, cpu_gene.kills) * (20 + (cpu_gene.level * 5))):
            #         mc_satisfy = False
            #         model_handler.network = None
            #         print("failed! -> " + str((cpu_gene.kills + 1)
            #               * (20 + (cpu_gene.level * 10))))
            #         id, agent, cpu_gene = get_next(queue_1)

            #         model_handler.reset(agent)
            #         cpu_gene.level = cpu_level
            #     elif score.kills >= cpu_gene.kills and score.total_damage >= cpu_gene.damage:
            #         mc_satisfy = True
            #         model_handler.network = None
            #         id, agent, cpu_gene = get_next(queue_1)
            #         model_handler.reset(agent)
            #         cpu_gene.level = cpu_level
            #         print(str((cpu_gene.kills) * (20 + (cpu_gene.level * 10))))
            #         # print("Success!")

            if (player0 and player0.stock == 0 or player1 and player1.stock == 0):
                # print(score)
                # print("no stocks! game over -> Satisfied: " + str(mc_satisfy))
                # id, agent, cpu_gene = get_next(queue_1)
                print(cpu_gene)
                ai_controller_index = (ai_controller_index + 1) % 2
                cpu_gene.character = random.choice(characters)
                cpu_gene.cpu_character = random.choice(characters)
                cpu_gene.controller_id = ai_controller_index
                cpu_level = random.randint(3, 9)
                cpu_gene.level = cpu_level
                cpu_gene.stage = random.choice(stages)
                aiDef = aiControllerDef(cpu_gene, controller,
                                        controller_opponent, player_index, opponent_index)
                cpuDef = opponentControllerDef(
                    cpu_gene, controller, controller_opponent, player_index, opponent_index)
                model_handler = ModelHandlerMCC_CPU(cpu_gene.controller_id, aiDef.player_index, cpuDef.player_index,
                                                    aiDef.controller, controller_helper, configuration.evaluator)
                model_handler.reset(agent)
                controller_opponent.release_all()
                controller.release_all()

        else:

            leftSide, rightSide = controllerDefs(
                cpu_gene, controller, controller_opponent, player_index, opponent_index)
            if check_controller_status:
                print(cpu_gene)
                print(cpu_gene.controller_id)
                print(str(model_handler.model_index) + " vs " +
                      str(model_handler.opponent_index))
                if leftSide.level == 0:
                    leftSideStatus = melee.ControllerStatus.CONTROLLER_HUMAN
                else:
                    leftSideStatus = melee.ControllerStatus.CONTROLLER_CPU
                if (game_state.players[leftSide.player_index].controller_status != leftSideStatus):
                    melee.MenuHelper.change_controller_status(
                        leftSide.controller, game_state, leftSide.player_index, leftSideStatus)
                if rightSide.level == 0:
                    rightSideStatus = melee.ControllerStatus.CONTROLLER_HUMAN
                else:
                    rightSideStatus = melee.ControllerStatus.CONTROLLER_CPU
                if (game_state.players[rightSide.player_index].controller_status != rightSideStatus):
                    melee.MenuHelper.change_controller_status(
                        rightSide.controller, game_state, rightSide.player_index, rightSideStatus)

                if game_state.players[leftSide.player_index].controller_status == leftSideStatus and game_state.players[rightSide.player_index].controller_status == rightSideStatus:
                    check_controller_status = False
            else:
                menu_helper_simple(game_state,
                                   leftSide.controller,
                                   leftSide.character,
                                   cpu_gene.stage,
                                   args.connect_code,
                                   costume=0,
                                   autostart=False,
                                   swag=False,
                                   cpu_level=leftSide.level)
                if game_state.players:
                    player: melee.PlayerState = game_state.players[leftSide.player_index]
                    player1: melee.PlayerState = game_state.players[rightSide.player_index]

                    if player and player.cpu_level == leftSide.level and player.character == leftSide.character:
                        choose_character(
                            character=rightSide.character,
                            gamestate=game_state,
                            controller=rightSide.controller,
                            cpu_level=rightSide.level,
                            costume=0,
                            swag=False,
                            start=True)
                    if game_state.menu_state == melee.Menu.STAGE_SELECT:
                        # print("in stage selection")
                        if player and player.cpu_level == leftSide.level and player.character == leftSide.character and player1 and player1.cpu_level == rightSide.level and player1.character == rightSide.character:
                            melee.MenuHelper.choose_stage(
                                cpu_gene.stage, game_state, controller_opponent)


def controllerDefs(cpu_gene: CPUGene, controller: melee.Controller, controller_opponent: melee.Controller, player_index: int, opponent_index: int):
    if cpu_gene.controller_id == 0:
        leftSide = ControllerDef(
            0, cpu_gene.character, controller, player_index)
        rightSide = ControllerDef(
            cpu_gene.level, cpu_gene.cpu_character, controller_opponent, opponent_index)
    else:
        leftSide = ControllerDef(
            cpu_gene.level, cpu_gene.cpu_character, controller, player_index)
        rightSide = ControllerDef(
            0, cpu_gene.character, controller_opponent, opponent_index)
    return (leftSide, rightSide)


def aiControllerDef(cpu_gene: CPUGene, controller: melee.Controller, controller_opponent: melee.Controller, player_index: int, opponent_index: int):
    if cpu_gene.controller_id == 0:
        return ControllerDef(
            0, cpu_gene.character, controller, player_index)
    else:
        return ControllerDef(
            0, cpu_gene.character, controller_opponent, opponent_index)


def opponentControllerDef(cpu_gene: CPUGene, controller: melee.Controller, controller_opponent: melee.Controller, player_index: int, opponent_index: int):
    if cpu_gene.controller_id == 0:
        return ControllerDef(
            cpu_gene.level, cpu_gene.cpu_character, controller_opponent, opponent_index)
    else:
        return ControllerDef(
            cpu_gene.level, cpu_gene.cpu_character, controller, player_index)


def queueCpuGeneMCC(queue: mp.Queue):
    host = "192.168.0.100"
    model_helper = ModelHelperMCC_CPUGene(host)

    while True:
        try:
            id, builder = model_helper.randomBest(0)
            network = builder.create_ndarrays(sigmoidal)
            cpu_gene = CPUGene(3, 1, 1, 0, 100, melee.Stage.FINAL_DESTINATION,
                               melee.Character.FALCO, melee.Character.FALCO, 0, 0, 0)
            last_data = (id, network, cpu_gene)
            queue.put(last_data)
        except:
            queue.put(last_data)


if __name__ == '__main__':
    mgr = mp.Manager()
    mgr_dict = mgr.dict()
    ns = mgr.Namespace()
    # ns = mgr.Namespace()
    # host = "localhost"
    # port = 8095
    process_num = 15
    r = get("http://192.168.0.100:8091/configuration")
    data = r.json()
    configuration = processConfiguration(data)

    processes: List[mp.Process] = []
    queue_1 = mgr.Queue(process_num)

    for i in range(process_num):
        p = mp.Process(target=console_loop_mcc_cpu_gene,
                       args=(queue_1, configuration, i + 51460), daemon=True)
        processes.append(p)
        p.start()
        p = mp.Process(target=queueCpuGeneMCC, daemon=True,
                       args=(queue_1, ))
        processes.append(p)
        p.start()
    for p in processes:
        p.join()
