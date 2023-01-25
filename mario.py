from cmath import inf
from multiprocessing.managers import DictProxy, Namespace
from typing import Any, Dict, List
from xmlrpc.client import Boolean
from gym.core import Env
from nes_py.wrappers import JoypadSpace
import gym_super_mario_bros
import asyncio
import numpy as np
import math

from httpx import get, post

import pyglet
import time
import multiprocessing as mp
from skimage.transform import rescale, resize, downscale_local_mean
import time
# import cv2 as cv
from gym_super_mario_bros.actions import COMPLEX_MOVEMENT
from dataclasses import dataclass
from dacite import from_dict

from ComputableNetwork import ComputableNetwork, relu, sigmoidal
from NeatService import process_model_data, process_model_data_mcc, process_model_data_mcc_stage, StageGene, StageTrackGene


def submitScore(data, host: str):
    # print(info["stage"])
    post("http://" + host + ":8095/score", json=data)


def deadNetwork(host: str):
    post("http://" + host + ":8095/dead", json={"id": id})


def statusValue(status):
    if (status == "small"):
        return 0
    elif (status == "tall"):
        return 1
    else:
        return 4


def rgb2gray(rgb):
    return np.dot(rgb[..., :3], [0.2989, 0.5870, 0.1140])


class GameEventHelper:

    def mushroom_found(self, info, prev_info):
        return info["status"] == "tall" and prev_info["status"] == "small"

    def fire_flower_found(self, info, prev_info):
        return info["status"] == "fireball" and prev_info["status"] == "tall"

    def coins_collected(self, info, prev_info):
        return info["coins"] - prev_info["coins"]

    def score_collected(self, info, prev_info):
        return info["score"] - prev_info["score"]

    def flag_reached(self, info, prev_info):
        return not prev_info["flag_get"] and info["flag_get"]

    def one_up_found(self, info, prev_info):
        return prev_info["life"] < info["life"]

    def stage_part_complete(self, info, stage_part_position: int):
        return (info["x_pos"] / 32) > stage_part_position


class GameEventCollector:
    game_event_helper: GameEventHelper
    prev_info: Any
    mushrooms: int
    fire_flowers: int
    coins: int
    score: int
    lifes: int
    flags: int
    start_stage_part: int
    stage_parts: int
    time: int

    def __init__(self, game_event_helper: GameEventHelper,
                 start_stage_part: int) -> None:
        self.game_event_helper = game_event_helper
        self.start_stage_part = start_stage_part
        self.coins = 0
        self.mushrooms = 0
        self.fire_flowers = 0
        self.score = 0
        self.lifes = 0
        self.flags = 0
        self.stage_parts = 0
        self.time = 0
        self.prev_info = None

    def process_frame(self, info):
        if self.prev_info:
            if self.game_event_helper.mushroom_found(info, self.prev_info):
                self.mushrooms += 1
            if self.game_event_helper.fire_flower_found(info, self.prev_info):
                self.fire_flowers += 1
            if self.game_event_helper.coins_collected(info, self.prev_info):
                self.coins += 1
            self.score += self.game_event_helper.score_collected(
                info, self.prev_info)
            if self.game_event_helper.one_up_found(info, self.prev_info):
                self.lifes += 1
            if self.game_event_helper.stage_part_complete(
                    info, self.start_stage_part):
                self.start_stage_part += 1
                self.stage_parts += 1
            if self.game_event_helper.flag_reached(info, self.prev_info):
                self.flags += 1
                self.start_stage_part = 0
            if not self.prev_info["time"] == info["time"]:
                self.time += 1
        self.prev_info = info


def get_network_novelty(host: str, port: int):
    res = get("http://" + host + ":" + str(port) +
              "/model/request", timeout=100)
    if not res.is_success:
        raise Exception("No data for request")
    data = res.json()
    id, builder = process_model_data(data)

    return id, builder


def marioNovelty(queue: mp.Queue, render: Boolean):
    env = gym_super_mario_bros.make('SuperMarioBros-1-2-v1')
    env = JoypadSpace(env, COMPLEX_MOVEMENT)
    host = "192.168.0.100"
    port = 8095
    done = False
    network: ComputableNetwork
    # id, child_network = get_network_novelty(host, port)
    id, child_network = queue.get()
    state = None
    score = 0
    stage = 0
    action = 0  # no op
    last_action = 0
    state = env.reset()
    prevX = 0
    prevXReset = 0
    framesSinceMaxXChange = 0
    status = "small"
    startInfo = None
    idle = False
    game_event_helper = GameEventHelper()
    game_event_collector = GameEventCollector(game_event_helper, 1)
    steps_left = 0
    steps_right = 0
    same_action_counter = 0
    last_stage_part = 0

    while True:
        if done or idle:
            state = env.reset()
            submitScore(
                {
                    "id": id,
                    "life": int(info["life"]),
                    "time": int(game_event_collector.time),
                    "world": int(info["world"]),
                    "stage": int(info["stage"]),
                    "yPos": int(info["y_pos"]),
                    "xPos": int(info["x_pos"]),
                    "score": int(game_event_collector.score),
                    "coins": int(game_event_collector.coins),
                    "mushrooms": int(game_event_collector.mushrooms),
                    "fireFlowers": int(game_event_collector.fire_flowers),
                    "flags": int(game_event_collector.flags),
                    "lifes": int(game_event_collector.lifes),
                    "stageParts": int(game_event_collector.stage_parts),
                    "status": str(info["status"]),
                }, host)
            id, child_network = queue.get()
            startInfo = None
            prevX = 0
            steps_left = 0
            steps_right = 0
            framesSinceMaxXChange = 0
            game_event_collector = GameEventCollector(game_event_helper, 1)
            idle = False
            last_stage_part = 0
            same_action_counter = 0
            child_active = True

        state, reward, done, info = env.step(action)
        game_event_collector.process_frame(info)
        if (startInfo == None):
            startInfo = info
        if (status != info["status"]):
            framesSinceMaxXChange = 0
        if (stage != info["stage"]):
            framesSinceMaxXChange = 0

        status = info["status"]
        stage = info["stage"]
        # print(state.shape)
        state = rescale(
            rgb2gray(state),
            1 / 16,
            # channel_axis=2
        )
        # state = state  * np.random.binomial(1, .25,  state.size).reshape(state.shape)
        network = child_network
        network.input((state / 42.5) - 3)
        network.compute()
        output = network.output()
        # if (score != info["score"]):
        #     framesSinceMaxXChange = 0
        #     score = info["score"]
        if abs(prevX - info["x_pos"]) > 16:
            # if prevX > info["x_pos"] and abs(prevXReset - info["x_pos"]) > 4:
            #     steps_left += 1
            #     prevXReset = info["x_pos"]
            # else:
            #     steps_right += 1
            framesSinceMaxXChange = 0
            prevX = info["x_pos"]
        else:
            framesSinceMaxXChange += 1
        framesSinceMaxXChange = max(-10 * 20, framesSinceMaxXChange)

        if framesSinceMaxXChange > 20 * 20 or reward < -14:
            idle = True

        action = output.argmax(1)[0]
        # print(output)
        # print(output.shape)
        # print(action)

        # if action != last_action or action == 0:
        #     framesSinceMaxXChange += max(0, 5 - same_action_counter)
        #     same_action_counter = max(0, same_action_counter - .1)
        # else:
        #     same_action_counter += .2
        last_action = action
        if render:
            env.render()


def mario_mcc(queue: mp.Queue, render: Boolean):
    env = gym_super_mario_bros.make('SuperMarioBros-v1')
    env = JoypadSpace(env, COMPLEX_MOVEMENT)
    host = "localhost"
    done = False
    network: ComputableNetwork
    id, agent_network, child_network = queue.get()
    evaluated_child = False
    evaluated_agent = False
    state = None
    score = 0
    stage = 0
    action = 0  # no op
    last_action = 0
    state = env.reset()
    prevX = 0
    prevXReset = 0
    framesSinceMaxXChange = 0
    status = "small"
    startInfo = None
    idle = False
    game_event_helper = GameEventHelper()
    game_event_collector = GameEventCollector(game_event_helper, 0)
    steps_left = 0
    steps_right = 0
    same_action_counter = 0
    child_x = 0
    agent_x = 0
    while True:
        if evaluated_child:
            network = agent_network
        else:
            network = child_network
        if done or idle:
            state = env.reset()
            # death
            if not evaluated_child:
                if int(info["x_pos"]) > 50:
                    child_x = int(info["x_pos"]) / 10_000 + \
                        int(info["stage"]) + int(info["world"]) * 10
                else:
                    child_x = int(info["stage"]) + int(info["world"]) * 10
                evaluated_child = True
                # print("child: " + str(child_x))
                done = False
                idle = False
                framesSinceMaxXChange = 0
                same_action_counter = 0
                prevX = 0
                steps_left = 0
                steps_right = 0
            elif not evaluated_agent:
                evaluated_agent = True
                agent_x = int(info["x_pos"]) / 10_000 + \
                    int(info["stage"]) + int(info["world"]) * 10
                # print("agent: " + str(agent_x))
            if evaluated_agent and evaluated_child:
                mc_satisfy = child_x > agent_x
                print(id + ": agent: " + str(agent_x) + " child: " +
                      str(child_x) + " -> " + str(mc_satisfy))
                submitScore(
                    {
                        "id": id,
                        "satisfyMC": bool(mc_satisfy)
                    }, host)
                id, agent_network, child_network = queue.get()
                startInfo = None
                prevX = 0
                steps_left = 0
                steps_right = 0
                framesSinceMaxXChange = 0
                game_event_collector = GameEventCollector(game_event_helper, 0)
                idle = False
                evaluated_child = False
                evaluated_agent = False
                child_x = 0
                agent_x = 0
                same_action_counter = 0

        state, reward, done, info = env.step(action)
        game_event_collector.process_frame(info)
        if (startInfo == None):
            startInfo = info
        if (status != info["status"]):
            framesSinceMaxXChange = 0
        if (stage != info["stage"]):
            framesSinceMaxXChange = 0

        status = info["status"]
        stage = info["stage"]
        # print(state.shape)
        state = rescale(
            rgb2gray(state),
            1 / 8,
            # channel_axis=2
        )  # * np.random.binomial(1, .25,  state.size)
        # state = state  * np.random.binomial(1, .25,  state.size).reshape(state.shape)
        network.input(state / 255.0)
        # network.input((state / 42.5) - 3)
        network.compute()
        output = network.output()
        # if (score != info["score"]):
        #     framesSinceMaxXChange = 0
        #     score = info["score"]
        if abs(prevX - info["x_pos"]) > 16:
            if prevX > info["x_pos"] and abs(prevXReset - info["x_pos"]) > 4:
                steps_left += 1
                prevXReset = info["x_pos"]
            else:
                steps_right += 1
            framesSinceMaxXChange = 0
            prevX = info["x_pos"]

        else:
            framesSinceMaxXChange += 1
        framesSinceMaxXChange = max(-10 * 20, framesSinceMaxXChange)

        if framesSinceMaxXChange > 20 * 20 or reward < -14:
            idle = True

        action = 11 - output.argmax(1)[0]
        # print(output.shape)
        # print(action)

        # if action != last_action or action == 0:
        #     framesSinceMaxXChange += max(0, 5 - same_action_counter)
        #     same_action_counter = max(0, same_action_counter - .1)
        # else:
        #     same_action_counter += .2
        last_action = action
        if render:

            env.render()


def mario_mcc_stage(queue: mp.Queue, render: Boolean):
    env_mariogym = gym_super_mario_bros.make('SuperMarioBrosRandomStages-v1')
    env = JoypadSpace(env_mariogym, COMPLEX_MOVEMENT)
    host = "192.168.0.100"
    done = False
    stage_track_gene: StageTrackGene
    agent_network: ComputableNetwork
    agent_id: str
    environment_id: str
    population_type: str
    population_type, agent_id, environment_id, agent_network, stage_track_gene = queue.get() #getNextModel()
    state = None
    action = 0  # no op
    prevX = 0
    framesSinceMaxXChange = 0 * 20
    status = "small"
    idle = False
    game_event_helper = GameEventHelper()
    game_event_collector = GameEventCollector(game_event_helper, 0)

    agent_x = 0
    stage_index = 0
    stage_gene = stage_track_gene.stages[stage_index]
    print("stage track: " + environment_id)
    print("stage index: " + str(stage_index) + " -> " + str(stage_gene.world)+"-"+str(stage_gene.stage) +
          " d=" + str(stage_gene.distance) + " c=" + str(stage_gene.coin) + " s=" + str(stage_gene.score))
    state = env_mariogym.reset(options={
        "stages": [str(stage_gene.world)+"-"+str(stage_gene.stage)]
    })

    # Iterate through each stage in track
    # exit the stage when dead, run out of time alotted, flag reached or completed the stageGene
    # Continue through iteration until complete or first failure
    while True:
        if done or idle:

            agent_x = int(info["x_pos"])
            coin = int(info["coins"])
            score = int(info["score"])
            stage_gene = stage_track_gene.stages[stage_index]
            mc_satisfy = (agent_x > stage_gene.distance or bool(info["flag_get"])) and int(
                info["coins"]) >= stage_gene.coin and int(info["score"]) >= stage_gene.score
            print("stage index: " + str(stage_index) + " -> " + str(stage_gene.world)+"-"+str(stage_gene.stage) + " d=" + str(stage_gene.distance) + " c=" +
                  str(stage_gene.coin) + " s=" + str(stage_gene.score) + " ---- " + str(agent_x) + ", " + str(coin) + ", " + str(score) + " - Satisfied: " + str(mc_satisfy))

            # print(id + ": agent: " + str(agent_x) + " child: " + str(child_x) + " -> " + str(mc_satisfy))
            if not mc_satisfy or stage_index + 1 == len(stage_track_gene.stages):
                submitScore(
                    {
                        "agentId": agent_id,
                        "environmentId": environment_id,
                        "type": population_type,
                        "satisfyMC": bool(mc_satisfy),
                        "dead": agent_x <= 40
                    }, host)

                population_type, agent_id, environment_id, agent_network, stage_track_gene = queue.get() #getNextModel()getNextModel()
                stage_index = 0
                stage_gene = stage_track_gene.stages[stage_index]
                state = env_mariogym.reset(options={
                    "stages": [str(stage_gene.world)+"-"+str(stage_gene.stage)]
                })
                print("stage track: " + environment_id + " & ID: " + agent_id)
                print("stage index: " + str(stage_index) + " -> " + str(stage_gene.world)+"-"+str(stage_gene.stage) +
                      " d=" + str(stage_gene.distance) + " c=" + str(stage_gene.coin) + " s=" + str(stage_gene.score))
                prevX = 40
                framesSinceMaxXChange = 0 * 20
                game_event_collector = GameEventCollector(game_event_helper, 0)
                idle = False
            else:
                idle = False
                prevX = 40
                framesSinceMaxXChange = 0 * 20
                stage_index += 1
                stage_gene = stage_track_gene.stages[stage_index]
                state = env_mariogym.reset(options={
                    "stages": [str(stage_gene.world)+"-"+str(stage_gene.stage)]
                })
                print("stage index: " + str(stage_index) + " -> " + str(stage_gene.world)+"-"+str(stage_gene.stage) +
                      " d=" + str(stage_gene.distance) + " c=" + str(stage_gene.coin) + " s=" + str(stage_gene.score))

        state, reward, done, info = env.step(action)
        game_event_collector.process_frame(info)

        if (status != info["status"]):
            framesSinceMaxXChange = 0
        status = info["status"]
        state = rescale(
            rgb2gray(state),
            1 / 8,
            # channel_axis=2
        )  # * np.random.binomial(1, .25,  state.size)
        # state = state  * np.random.binomial(1, .25,  state.size).reshape(state.shape)
        agent_network.input(state / 255.0)
        # network.input((state / 42.5) - 3)
        agent_network.compute()
        output = agent_network.output()
        if abs(prevX - info["x_pos"]) > 32:
            framesSinceMaxXChange = 0
            prevX = info["x_pos"]

        else:
            framesSinceMaxXChange += 1
        framesSinceMaxXChange = max(-10 * 20, framesSinceMaxXChange)
        stage_gene = stage_track_gene.stages[stage_index]
        if framesSinceMaxXChange > 60 * 20 or reward < -14 or info["x_pos"] > stage_gene.distance:
            idle = True

        action = 11 - output.argmax(1)[0]
        if render:
            env.render()


def actionToNdArray(value: int):
    array = np.zeros([1, 12])
    array[0, value] = 1
    return array


def queueNetworks(queue: mp.Queue, mgr_dict: DictProxy, ns: Namespace):
    host = "192.168.0.100"
    port = 8095
    ns.generation = 0
    while True:
        # try:
        id, builder = get_network_novelty(host, port)
        if id not in mgr_dict:
            mgr_dict[id] = True
            network = builder.create_ndarrays(sigmoidal)
            ns.generation += 1
            queue.put((id, network))
        if ns.generation > 100_000:
            mgr_dict.clear()
            ns.generation = 0

        # except:
        #     print("failed to get network...")


def get_network_mcc(host: str, port: int):
    res = get("http://" + host + ":" + str(port) + "/model", timeout=100)
    if not res.is_success:
        raise Exception("No data for request")
    data = res.json()
    id, builder, child = process_model_data_mcc(data)

    return id, builder, child


def get_network_mcc_stage(host: str, port: int):
    res = get("http://" + host + ":" + str(port) + "/model", timeout=30)
    if not res.is_success:
        raise Exception("No data for request")
    data = res.json()

    agent_id = data["agentId"]
    environment_id = data["environmentId"]
    population_type = data["type"]
    id, agent, environment = process_model_data_mcc_stage(data)

    return population_type, agent_id, environment_id, agent, environment

# def queueNetworkPairs(queue : mp.Queue, mgr_dict : DictProxy, ns : Namespace):
#     host = "192.168.0.100"
#     port = 8095
#     ns.generation = 0
#     while True:
#         # try:
#         try:
#             id, builder_agent, builder_child = get_network_mcc(host, port)
#             if id not in mgr_dict:
#                 mgr_dict[id] = True
#                 network = builder_agent.create_ndarrays(sigmoidal)
#                 network_child = builder_child.create_ndarrays(sigmoidal)
#                 ns.generation += 1
#                 queue.put((id, network, network_child))
#             if ns.generation > 100_000:
#                 mgr_dict.clear()
#                 ns.generation = 0

#         except:
#             print("failed to get network...")

# def queue_network_mcc_stage(queue : mp.Queue):
#     host = "192.168.0.100"
#     port = 8095

#     while True:
#         try:

#             id, builder_agent, environment = get_network_mcc_stage(host, port)

#             network = builder_agent.create_ndarrays(sigmoidal)
#             queue.put((id, network, environment))


#         except:
#             print("failed to get network...")

def getNextModel():
    host = "192.168.0.100"
    port = 8095
    tryCount = 0
    while True:
        try:
            population_type, agent_id, environment_id, builder_agent, environment = get_network_mcc_stage(host, port)

            network = builder_agent.create_ndarrays(sigmoidal)
            return (population_type, agent_id, environment_id, network, environment)
        except:
            if tryCount < 5:
                get("http://localhost:8095/fillModels")
                tryCount +=1
            else:
                exit()
            print("failed to get network...")


def queueModels(queue : mp.Queue):
    host = "192.168.0.100"
    port = 8095
    tryCount = 0
    while True:
        try:
            population_type, agent_id, environment_id, builder_agent, environment = get_network_mcc_stage(host, port)
            tryCount =0
            network = builder_agent.create_ndarrays(sigmoidal)
            queue.put((population_type, agent_id, environment_id, network, environment))
        except:
            
            get("http://localhost:8095/fillModels")
            
            print("failed to get network...")


if __name__ == '__main__':
    mgr = mp.Manager()
    mgr_dict = mgr.dict()
    ns = mgr.Namespace()
    # ns = mgr.Namespace()
    # host = "localhost"
    # port = 8095
    process_num = 8
    queue = mgr.Queue(process_num * 2)
    processes: List[mp.Process] = []

    for i in range(process_num):
        p = mp.Process(target=mario_mcc_stage,
                       daemon=True, args=(queue, i < 5))
        processes.append(p)
        p.start()
        p = mp.Process(target=queueModels, daemon=True, args=(queue,))
        processes.append(p)
        p.start()
        p = mp.Process(target=queueModels, daemon=True, args=(queue,))
        processes.append(p)
        p.start()
        # p = mp.Process(target=queueNetworks, daemon=True, args=(queue,mgr_dict, ns))
        # processes.append(p)
        # p.start()

    for p in processes:
        p.join()
    # mario()
