#!/usr/bin/python3

import multiprocessing as mp

from multiprocessing.managers import DictProxy, Namespace
import random
import time
from typing import Any, List, Tuple
from urllib import response
from httpx import ReadTimeout, get

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


def get_next(queue: mp.Queue) -> Tuple[str, str, str, HyperNeatBuilder, CPUGene]:
    return queue.get()


def console_loop_mcc_cpu_gene(port: int, queue_1: mp.Queue, configuration: Configuration, queue_result: mp.Queue):
    console, controller, controller_opponent, args, log = startConsole(port)
    player_index = args.port
    opponent_index = args.opponent

    controller_orig = controller
    controller_opponent_orig = controller_opponent
    # if random.random() >= .5:
    #     player_index = args.opponent
    #     opponent_index = args.port
    #     temp_controller = controller_orig
    #     controller = controller_opponent_orig
    #     controller_opponent = temp_controller
    # print(configuration.player_1.character)
    ai_controller_id = 0
    reset = 0
    host = "192.168.0.100"
    model_helper = ModelHelperMCC_CPUGene(host)
    controller_helper = ControllerHelper()
    population_type, agent_id, environment_id, agent, cpu_gene = get_next(
        queue_1)
    aiDef = aiControllerDef(cpu_gene, controller,
                            controller_opponent, player_index, opponent_index)
    cpuDef = opponentControllerDef(
        cpu_gene, controller, controller_opponent, player_index, opponent_index)
    model_handler = ModelHandlerMCC_CPU(cpu_gene.controller_id, aiDef.player_index, cpuDef.player_index,
                                        aiDef.controller, controller_helper, configuration.evaluator)
    print(cpu_gene)
    # Figure out which handlers to pass the networks to
    model_handler.reset(agent)
    check_controller_status = False
    while True:
        game_state = console.step()
        if game_state is None:
            print("We hit this None BS => " + str(cpu_gene))
            continue

        if game_state.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:

            check_controller_status = True
            player0: PlayerState = game_state.players[player_index]
            player1: PlayerState = game_state.players[opponent_index]
            model_handler.evaluate(game_state)
            score = model_handler.evaluator.score()
            # if (score.total_frames_alive % 60 * 20 == 0):
            #     print(score)
            if not (model_handler.network == None):
                if (score.deaths >= cpu_gene.deaths or score.total_damage_taken >= cpu_gene.damage_taken or score.total_frames_alive / 60 > max(1, .5 + cpu_gene.kills) * (30 + cpu_gene.level * 5)):
                    mc_satisfy = False
                    model_handler.network = None
                    queue_result.put(EvalResultCPU(
                        population_type, agent_id, environment_id, mc_satisfy, False))

                    # print(score)

                    # print("failed!")
                elif score.kills >= cpu_gene.kills and score.total_damage >= cpu_gene.damage and score.unique_actions >= cpu_gene.unique_actions and score.ground_movement_distance >= cpu_gene.ground_movement_distance:
                    mc_satisfy = True
                    model_handler.network = None
                    queue_result.put(EvalResultCPU(
                        population_type, agent_id, environment_id, mc_satisfy, False))
                    mc_satisfy = False
                    # print(score)

                    # print("Success!")

            if (player0 and player0.stock == 0 or player1 and player1.stock == 0) and model_handler.network == None:
                reset = 0
                population_type, agent_id, environment_id, agent, cpu_gene = get_next(
                    queue_1)
                # print(cpu_gene)
                aiDef = aiControllerDef(cpu_gene, controller,
                                        controller_opponent, player_index, opponent_index)
                cpuDef = opponentControllerDef(
                    cpu_gene, controller, controller_opponent, player_index, opponent_index)
                model_handler = ModelHandlerMCC_CPU(cpu_gene.controller_id, aiDef.player_index, cpuDef.player_index,
                                                    aiDef.controller, controller_helper, configuration.evaluator)
                if (agent_id != "fakeID"):
                    model_handler.reset(agent)
                # else:
                #     print(id)

                controller_opponent.release_all()
                controller.release_all()

        else:
            reset += 1
            if reset > 60 and model_handler.network == None:
                # print("fake agent"+str(port))
                reset = 0
                population_type, agent_id, environment_id, agent, cpu_gene = get_next(
                    queue_1)
                aiDef = aiControllerDef(cpu_gene, controller,
                                        controller_opponent, player_index, opponent_index)
                cpuDef = opponentControllerDef(
                    cpu_gene, controller, controller_opponent, player_index, opponent_index)
                model_handler = ModelHandlerMCC_CPU(cpu_gene.controller_id, aiDef.player_index, cpuDef.player_index,
                                                    aiDef.controller, controller_helper, configuration.evaluator)
                if (agent_id != "fakeID"):
                    model_handler.reset(agent)

            leftSide, rightSide = controllerDefs(
                cpu_gene, controller, controller_opponent, player_index, opponent_index)
            if check_controller_status and model_handler.network != None:
                if reset > 60 * 10:
                    reset = 0
                    print("stuck........."+str(port))
                if game_state.menu_state in [melee.Menu.STAGE_SELECT]:
                    #just in case we enter the stage select with B held down
                    if reset < 10:
                        controller.release_button(melee.Button.BUTTON_B)
                    else:
                        controller.press_button(melee.Button.BUTTON_B)
                elif game_state.menu_state in [melee.Menu.CHARACTER_SELECT]:
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
            elif model_handler.network != None:
                if reset > 60 *10:
                    reset =0
                    print("stuck......... 2 - " + str(cpu_gene))
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
            else:
                if reset > 60 *10:
                    print("stuck......... 3")


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
    sleep = 0
    while True:
        try:
            if (sleep == 0):
                population_type, agent_id, environment_id, builder, cpu_gene = model_helper.getNetworks()
                network = builder.create_ndarrays(sigmoidal, sigmoidal)

            last_data = (population_type, agent_id,
                         environment_id, network, cpu_gene)
            if (agent_id == "fakeID" and queue.empty()) or agent_id != "fakeID":
                queue.put(last_data)
            if agent_id == "fakeID":
                sleep +=1
                time.sleep(.01)
            if (sleep > 100):
                sleep = 0
            
            
            
        except:
            queue.put(last_data)


def httpRequestProcess(queue: mp.Queue):
    host = "192.168.0.100"
    model_helper = ModelHelperMCC_CPUGene(host)
    request_data: EvalResultCPU
    while True:
        request_data = queue.get()
        model_helper.send_evaluation_result(
            request_data)
        # print(request_data)


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
    queue_result = mgr.Queue(process_num)

    p = mp.Process(target=httpRequestProcess, daemon=True,
                   args=(queue_result, ))
    processes.append(p)
    p.start()

    for i in range(process_num):
        p = mp.Process(target=console_loop_mcc_cpu_gene, args=(
            i + 51460, queue_1, configuration, queue_result), daemon=True)
        processes.append(p)
        p.start()

        p = mp.Process(target=queueCpuGeneMCC, daemon=True,
                       args=(queue_1, ))
        processes.append(p)
        p.start()
    for p in processes:
        p.join()
