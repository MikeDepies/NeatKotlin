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
from ModelHelperMCC import ModelHelperMCC, EvalResult
from ModelHelperMCC_CPUGene import ModelHelperMCC_CPUGene, EvalResultCPU
from ModelHandler import ModelHandler
from ModelHandlerMCC import ModelHandlerMCC
from ModelHelper import ModelHelper
from meleeConsole import startConsole
from NeatComputation import HyperNeatBuilder

def console_loop(port: int, queue_1: mp.Queue, queue_2: mp.Queue, configuration: Configuration):
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
    #     # print(configuration.player_1.character)
    ai_controller_id = 0
    ai_controller_id2 = 1
    reset = 0
    controller_helper = ControllerHelper()
    model_handler = ModelHandler(ai_controller_id, player_index, opponent_index,
                                 controller, controller_helper, queue_1, configuration.evaluator)
    model_handler.reset()
    # model_handler2 = ModelHandler(ai_controller_id2, opponent_index, player_index, controller_opponent, controller_helper, queue_2, configuration.evaluator)
    # model_handler2.reset()
    while True:
        game_state = console.step()
        if game_state is None:
            print("We hit this None BS")
            continue

        if game_state.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:
            # print("game")
            player0: PlayerState = game_state.players[player_index]
            player1: PlayerState = game_state.players[opponent_index]
            # if model_handler2.network is not None:
            #     model_handler.evaluate(game_state)
            #     model_handler.postEvaluate(game_state)
            # else:
            #     controller.release_all()
            # if model_handler.network is not None:
            #     model_handler2.evaluate(game_state)
            #     model_handler2.postEvaluate(game_state)
            # else:
            #     controller_opponent.release_all()
            
            model_handler.evaluate(game_state)
            # model_handler2.evaluate(game_state)
            model_handler.postEvaluate(game_state)
            # model_handler2.postEvaluate(game_state)
            
            if player0 and player0.stock == 0 or player1 and player1.stock == 0:
                if model_handler.network is None:
                    model_handler.reset()
                # if model_handler2.network is None:
                #     model_handler2.reset()
                # print("no stocks! game over")
                controller_opponent.release_all()
                controller_opponent.flush()
                controller.release_all()
                controller.flush()
        else:
            melee.MenuHelper.menu_helper_simple(game_state,
                                                controller,
                                                configuration.player_1.character,
                                                configuration.stage,
                                                args.connect_code,
                                                costume=0,
                                                autostart=False,
                                                swag=False,
                                                cpu_level=configuration.player_1.cpu_level)
            # if game_state.players and game_state.players[player_index].character == melee.Character.MARIO:
            if game_state.players:
                player: melee.PlayerState = game_state.players[player_index]
                player1: melee.PlayerState = game_state.players[opponent_index]
                if player and player.cpu_level == configuration.player_1.cpu_level and player.character == configuration.player_1.character:
                    melee.MenuHelper.choose_character(
                        character=configuration.player_2.character,
                                        gamestate=game_state,
                                        controller=controller_opponent,
                                        cpu_level=configuration.player_2.cpu_level,
                                        costume=0,
                                        swag=False,
                                        start=True)
                if game_state.menu_state == melee.Menu.STAGE_SELECT:
                    if player and player.cpu_level == configuration.player_1.cpu_level and player.character == configuration.player_1.character and player1 and player1.cpu_level == configuration.player_2.cpu_level and player1.character == configuration.player_2.character:
                        print("ready")
                        # melee.MenuHelper.
                        melee.MenuHelper.choose_stage(configuration.stage, game_state, controller_opponent)

def get_next(queue : mp.Queue) -> Tuple[str, HyperNeatBuilder, HyperNeatBuilder, int, int]:
    return queue.get()

def console_loop_mcc(port: int, queue_1: mp.Queue, configuration: Configuration):
    console, controller, controller_opponent, args, log = startConsole(port)
    player_index = args.port
    opponent_index = args.opponent
    
    controller_orig = controller
    controller_opponent_orig = controller_opponent
    if random.random() >= .5:
        player_index = args.opponent
        opponent_index = args.port
        temp_controller = controller_orig
        controller = controller_opponent_orig
        controller_opponent = temp_controller
        # print(configuration.player_1.character)
    ai_controller_id = 0
    ai_controller_id2 = 1
    reset = 0
    host = "192.168.0.100"
    model_helper = ModelHelperMCC(host)
    controller_helper = ControllerHelper()
    model_handler = ModelHandlerMCC(ai_controller_id, player_index, opponent_index,
                                 controller, controller_helper, configuration.evaluator)
    model_handler2 = ModelHandlerMCC(ai_controller_id2, opponent_index, player_index, controller_opponent, controller_helper, configuration.evaluator)
    id, agent, child, agent_controller_id, child_controller_id = get_next(queue_1)
    # Figure out which handlers to pass the networks to
    if model_handler.ai_controller_id == agent_controller_id:
        model_handler.reset(agent)
        model_handler2.reset(child)
    else:
        model_handler.reset(child)
        model_handler2.reset(agent)
    
    
    while True:
        game_state = console.step()
        if game_state is None:
            print("We hit this None BS")
            continue

        if game_state.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:

            player0: PlayerState = game_state.players[player_index]
            player1: PlayerState = game_state.players[opponent_index]
            if agent_controller_id == model_handler.ai_controller_id:
                # model_handler.evaluator.frames_without_damage=0
                if model_handler.network is not None:
                    model_handler2.evaluate(game_state)
                    model_handler2.postEvaluate(game_state)
                else:
                    controller_opponent.release_all()
                if model_handler2.network is not None:
                    model_handler.evaluate(game_state)
                    model_handler.postEvaluate(game_state)
                else:
                    controller.release_all()
            else:
                # model_handler2.evaluator.frames_without_damage=0
                if model_handler2.network is not None:
                    model_handler.evaluate(game_state)
                    model_handler.postEvaluate(game_state)
                else:
                    controller.release_all()
                if model_handler.network is not None:
                    model_handler2.evaluate(game_state)
                    model_handler2.postEvaluate(game_state)
                else:
                    controller_opponent.release_all()
                
            
            if player0 and player0.stock == 0 or player1 and player1.stock == 0:
                # TODO: Handle MC satisfication problem and reset model handlers with new networks
                mc_satisfy = False
                
                if model_handler.network is None and model_handler.ai_controller_id == agent_controller_id:
                    mc_satisfy = model_handler2.evaluator.total_damage > 0 #len(model_handler2.evaluator.actions) > 5
                    print("satisfy: " + str(mc_satisfy) + " -> Agent: " + str(game_state.player[model_handler.model_index].character) + " against Child" + str(game_state.player[model_handler2.model_index].character))
                if model_handler2.network is None and model_handler2.ai_controller_id == agent_controller_id:
                    mc_satisfy = model_handler.evaluator.total_damage > 0 #len(model_handler.evaluator.actions) > 5
                    print("satisfy: " + str(mc_satisfy) + " -> Agent: " + str(game_state.player[model_handler2.model_index].character) + " against Child" + str(game_state.player[model_handler.model_index].character))
                if model_handler2.network is None and model_handler2.ai_controller_id == child_controller_id:
                    mc_satisfy = False
                    print("satisfy: " + str(mc_satisfy) + " -> Agent: " + str(game_state.player[model_handler.model_index].character) + " against Child" + str(game_state.player[model_handler2.model_index].character))
                if model_handler.network is None and model_handler.ai_controller_id == child_controller_id:
                    mc_satisfy = False
                    print("satisfy: " + str(mc_satisfy) + " -> Agent: " + str(game_state.player[model_handler2.model_index].character) + " against Child" + str(game_state.player[model_handler.model_index].character))
                
                model_helper.send_evaluation_result(EvalResult(id, mc_satisfy))
                    # model_handler.reset()
                # if model_handler2.network is None:
                    # model_handler2.reset()

                id, agent, child, agent_controller_id, child_controller_id = get_next(queue_1)
                if model_handler.ai_controller_id == agent_controller_id:
                    model_handler.reset(agent)
                    model_handler2.reset(child)
                else:
                    model_handler.reset(child)
                    model_handler2.reset(agent)
                # print("no stocks! game over")
                controller_opponent.release_all()
                controller_opponent.flush()
                controller.release_all()
                controller.flush()
        else:
            melee.MenuHelper.menu_helper_simple(game_state,
                                                controller,
                                                configuration.player_1.character,
                                                configuration.stage,
                                                args.connect_code,
                                                costume=0,
                                                autostart=False,
                                                swag=False,
                                                cpu_level=configuration.player_1.cpu_level)
            # if game_state.players and game_state.players[player_index].character == melee.Character.MARIO:
            if game_state.players:
                player: melee.PlayerState = game_state.players[player_index]
                player1: melee.PlayerState = game_state.players[opponent_index]
                if player and player.cpu_level == configuration.player_1.cpu_level and player.character == configuration.player_1.character:
                    melee.MenuHelper.choose_character(
                        character=configuration.player_2.character,
                                        gamestate=game_state,
                                        controller=controller_opponent,
                                        cpu_level=configuration.player_2.cpu_level,
                                        costume=0,
                                        swag=False,
                                        start=True)
                if game_state.menu_state == melee.Menu.STAGE_SELECT:
                    if player and player.cpu_level == configuration.player_1.cpu_level and player.character == configuration.player_1.character and player1 and player1.cpu_level == configuration.player_2.cpu_level and player1.character == configuration.player_2.character:
                        melee.MenuHelper.choose_stage(configuration.stage, game_state, controller_opponent)


def console_loop_mcc_cpu_gene(port: int, queue_1: mp.Queue, configuration: Configuration):
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
    ai_controller_id2 = 1
    reset = 0
    host = "192.168.0.100"
    model_helper = ModelHelperMCC_CPUGene(host)
    controller_helper = ControllerHelper()
    model_handler = ModelHandlerMCC(ai_controller_id, player_index, opponent_index,
                                 controller, controller_helper, configuration.evaluator)
    model_handler2 = ModelHandlerMCC(ai_controller_id2, opponent_index, player_index, controller_opponent, controller_helper, configuration.evaluator)
    id, agent, child, agent_controller_id, child_controller_id = get_next(queue_1)
    # Figure out which handlers to pass the networks to
    if model_handler.ai_controller_id == agent_controller_id:
        model_handler.reset(agent)
        model_handler2.reset(child)
    else:
        model_handler.reset(child)
        model_handler2.reset(agent)
    
    
    while True:
        game_state = console.step()
        if game_state is None:
            print("We hit this None BS")
            continue

        if game_state.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:

            player0: PlayerState = game_state.players[player_index]
            player1: PlayerState = game_state.players[opponent_index]
            if agent_controller_id == model_handler.ai_controller_id:
                # model_handler.evaluator.frames_without_damage=0
                if model_handler.network is not None:
                    model_handler2.evaluate(game_state)
                    model_handler2.postEvaluate(game_state)
                else:
                    controller_opponent.release_all()
                if model_handler2.network is not None:
                    model_handler.evaluate(game_state)
                    model_handler.postEvaluate(game_state)
                else:
                    controller.release_all()
            else:
                # model_handler2.evaluator.frames_without_damage=0
                if model_handler2.network is not None:
                    model_handler.evaluate(game_state)
                    model_handler.postEvaluate(game_state)
                else:
                    controller.release_all()
                if model_handler.network is not None:
                    model_handler2.evaluate(game_state)
                    model_handler2.postEvaluate(game_state)
                else:
                    controller_opponent.release_all()
                
            
            if player0 and player0.stock == 0 or player1 and player1.stock == 0:
                # TODO: Handle MC satisfication problem and reset model handlers with new networks
                mc_satisfy = False
                
                if model_handler.network is None and model_handler.ai_controller_id == agent_controller_id:
                    mc_satisfy = model_handler2.evaluator.total_damage > 0 #len(model_handler2.evaluator.actions) > 5
                    print("satisfy: " + str(mc_satisfy) + " -> Agent: " + str(game_state.player[model_handler.model_index].character) + " against Child" + str(game_state.player[model_handler2.model_index].character))
                if model_handler2.network is None and model_handler2.ai_controller_id == agent_controller_id:
                    mc_satisfy = model_handler.evaluator.total_damage > 0 #len(model_handler.evaluator.actions) > 5
                    print("satisfy: " + str(mc_satisfy) + " -> Agent: " + str(game_state.player[model_handler2.model_index].character) + " against Child" + str(game_state.player[model_handler.model_index].character))
                if model_handler2.network is None and model_handler2.ai_controller_id == child_controller_id:
                    mc_satisfy = False
                    print("satisfy: " + str(mc_satisfy) + " -> Agent: " + str(game_state.player[model_handler.model_index].character) + " against Child" + str(game_state.player[model_handler2.model_index].character))
                if model_handler.network is None and model_handler.ai_controller_id == child_controller_id:
                    mc_satisfy = False
                    print("satisfy: " + str(mc_satisfy) + " -> Agent: " + str(game_state.player[model_handler2.model_index].character) + " against Child" + str(game_state.player[model_handler.model_index].character))
                
                model_helper.send_evaluation_result(EvalResultCPU(id, mc_satisfy))
                    # model_handler.reset()
                # if model_handler2.network is None:
                    # model_handler2.reset()

                id, agent, child, agent_controller_id, child_controller_id = get_next(queue_1)
                if model_handler.ai_controller_id == agent_controller_id:
                    model_handler.reset(agent)
                    model_handler2.reset(child)
                else:
                    model_handler.reset(child)
                    model_handler2.reset(agent)
                # print("no stocks! game over")
                controller_opponent.release_all()
                controller_opponent.flush()
                controller.release_all()
                controller.flush()
        else:
            melee.MenuHelper.menu_helper_simple(game_state,
                                                controller,
                                                configuration.player_1.character,
                                                configuration.stage,
                                                args.connect_code,
                                                costume=0,
                                                autostart=False,
                                                swag=False,
                                                cpu_level=configuration.player_1.cpu_level)
            # if game_state.players and game_state.players[player_index].character == melee.Character.MARIO:
            if game_state.players:
                player: melee.PlayerState = game_state.players[player_index]
                player1: melee.PlayerState = game_state.players[opponent_index]
                if player and player.cpu_level == configuration.player_1.cpu_level and player.character == configuration.player_1.character:
                    melee.MenuHelper.choose_character(
                        character=configuration.player_2.character,
                                        gamestate=game_state,
                                        controller=controller_opponent,
                                        cpu_level=configuration.player_2.cpu_level,
                                        costume=0,
                                        swag=False,
                                        start=True)
                if game_state.menu_state == melee.Menu.STAGE_SELECT:
                    if player and player.cpu_level == configuration.player_1.cpu_level and player.character == configuration.player_1.character and player1 and player1.cpu_level == configuration.player_2.cpu_level and player1.character == configuration.player_2.character:
                        melee.MenuHelper.choose_stage(configuration.stage, game_state, controller_opponent)



def queueNetworks(queue: mp.Queue, controller_index: int):
    host = "192.168.0.100"
    model_helper = ModelHelper(controller_index, host)
    
    while True:
        # try:
        id, builder, best = model_helper.getNetwork(controller_index)
        network = builder.create_ndarrays(sigmoidal)
        if queue.qsize() == 0 and best:
            queue.put((id, network))
            time.sleep(1.0)
        elif not best:
            queue.put((id, network))


def queueNetworkPairs(queue: mp.Queue):
    host = "192.168.0.100"
    model_helper = ModelHelperMCC(host)
    
    while True:
        try:
            id, builder, child, agent_controller_id, child_controller_id = model_helper.getNetworks()
            network = builder.create_ndarrays(sigmoidal)
            child_network = child.create_ndarrays(sigmoidal)
            last_data = (id, network, child_network, agent_controller_id, child_controller_id)
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
    process_num = 25
    r = get("http://192.168.0.100:8091/configuration")
    data = r.json()
    configuration = processConfiguration(data)

    processes: List[mp.Process] = []
    queue_1 = mgr.Queue(process_num)
    queue_2 = mgr.Queue(process_num)
    
    for i in range(process_num):
        p = mp.Process(target=console_loop, args=(
            i + 51460, queue_1, queue_2, configuration), daemon=True)
        processes.append(p)
        p.start()
        p = mp.Process(target=queueNetworks, daemon=True,
                       args=(queue_1, 0 ))
        processes.append(p)
        p.start()
        # p = mp.Process(target=queueNetworks, daemon=True,
        #                args=(queue_2, 1 ))
        # processes.append(p)
        # p.start()
    for p in processes:
        p.join()
