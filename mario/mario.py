from cmath import inf
from typing import Any, Dict, List
from gym.core import Env
from nes_py.wrappers import JoypadSpace
import gym_super_mario_bros
import asyncio
import numpy as np
import math
import requests

from NeatNetwork import ComputableNetwork, ConnectionLocation, LayerShape3D, LayerPlane, NetworkBlueprint, constructNetwork
import time
import multiprocessing as mp
from skimage.transform import rescale, resize, downscale_local_mean
import time
# import cv2 as cv
from gym_super_mario_bros.actions import COMPLEX_MOVEMENT
from dataclasses import dataclass
from dacite import from_dict


def mapC(c):
    return LayerShape3D(
        LayerPlane(c["layerPlane"]["height"],
                                c["layerPlane"]["width"],
                                c["layerPlane"]["id"]), c["xOrigin"],
        c["yOrigin"], c["zOrigin"])


def toNetworkBlueprint(data : Any):
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
    output_layer = data["outputLayer"]
    return NetworkBlueprint( id, connections, connection_planes, connection_relationships, connection_relationships_inverse, calculation_order, output_layer)
    

def getNetwork(host: str):
    requestNetwork = True
    while requestNetwork:
        res = requests.get("http://" + host + ":8095/model")
        if not res.ok:
            time.sleep(2)
            continue
        data = res.json()
        child_blueprint = toNetworkBlueprint(data["child"])
        agent_blueprint = toNetworkBlueprint(data["agent"])
        
        child_network = constructNetwork(child_blueprint)
        agent_network = constructNetwork(agent_blueprint)

        return (child_blueprint.id, child_network, agent_network)


def getNetworkNovelty(host: str):
    requestNetwork = True
    while requestNetwork:
        res = requests.get("http://" + host + ":8095/model")
        if not res.ok:
            time.sleep(2)
            continue
        data = res.json()
        child_blueprint = toNetworkBlueprint(data)
        
        child_network = constructNetwork(child_blueprint)

        return (child_blueprint.id, child_network)


def submitScore(data, host: str):
    # print(info["stage"])
    requests.post("http://" + host + ":8095/score", json=data)


def deadNetwork(host: str):
    requests.post("http://" + host + ":8095/dead", json={"id": id})


def statusValue(status):
    if (status == "small"): return 0
    elif (status == "tall"): return 1
    else: return 4


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
        return (info["x_pos"] / 256) > stage_part_position


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


def mario2(render : bool):
    env = gym_super_mario_bros.make('SuperMarioBros-v1')
    env = JoypadSpace(env, COMPLEX_MOVEMENT)
    host = "localhost"
    done = False
    network : ComputableNetwork
    id, child_network, agent_network = getNetwork(host)
    evaluated_child = False
    evaluated_agent = False
    state = None
    score = 0
    stage = 0
    action = 0  #no op
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
                if int(info["x_pos"]) > 256:
                    child_x = int(info["x_pos"]) / 10_000 + int(info["stage"]) + int(info["world"]) * 10 
                else:
                    child_x = 0
                evaluated_child = True
                print("child: " + str(child_x))
                done = False
                idle = False
                framesSinceMaxXChange = 0
                same_action_counter = 0
                prevX = 0
                steps_left = 0
                steps_right = 0
            elif not evaluated_agent:
                evaluated_agent = True
                agent_x = int(info["x_pos"]) / 10_000 + int(info["stage"]) + int(info["world"]) * 10 
                print("agent: " + str(agent_x))
            if evaluated_agent and evaluated_child:
                mc_satisfy = child_x > agent_x
                submitScore(
                    {
                        "id": id,
                        "satisfyMC": bool(mc_satisfy)
                    }, host)
                id, child_network, agent_network = getNetwork(host)
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
            #channel_axis=2
        )  # * np.random.binomial(1, .25,  state.size)

        network.input(state / 255.0, actionToNdArray(action))
        network.compute()
        output = network.output()
        # if (score != info["score"]):
        #     framesSinceMaxXChange = 0
        #     score = info["score"]
        if abs(prevX - info["x_pos"]) > 32:
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
        
        if action != last_action or action == 0:
            framesSinceMaxXChange += max(0, 5 - same_action_counter)
            same_action_counter = max(0, same_action_counter - .1)
        else:
            same_action_counter += .2
        last_action = action
        if render:
            env.render()


def mario(render : bool):
    env = gym_super_mario_bros.make('SuperMarioBros-v1')
    env = JoypadSpace(env, COMPLEX_MOVEMENT)
    host = "localhost"
    done = False
    network : ComputableNetwork
    id, child_network, agent_network = getNetwork(host)
    child_active = True
    state = None
    score = 0
    stage = 0
    action = 0  #no op
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
        if evaluated_child:
            network = agent_network
        else:
            network = child_network
        if done or idle:
            state = env.reset()
            
            mc_satisfy = not child_active
            submitScore(
                {
                    "id": id,
                    "satisfyMC": bool(mc_satisfy)
                }, host)
            id, child_network, agent_network = getNetwork(host)
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
            1 / 8,
            #channel_axis=2
        )  # * np.random.binomial(1, .25,  state.size)
        if child_active:
            network = child_network
        else:
            network = agent_network
        network.input(state / 255.0, actionToNdArray(action))
        network.compute()
        output = network.output()
        # if (score != info["score"]):
        #     framesSinceMaxXChange = 0
        #     score = info["score"]
        if abs(prevX - info["x_pos"]) > 32:
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
        
        if last_stage_part != game_event_collector.stage_parts:
            print(game_event_collector.stage_parts)
            child_active = not child_active
            framesSinceMaxXChange = 0
            if child_active:
                print("Child is active")
            else:
                print("Agent is active")
        last_stage_part = game_event_collector.stage_parts
        if action != last_action or action == 0:
            framesSinceMaxXChange += max(0, 5 - same_action_counter)
            same_action_counter = max(0, same_action_counter - .1)
        else:
            same_action_counter += .2
        last_action = action
        if render:
            env.render()


def marioNovelty():
    env = gym_super_mario_bros.make('SuperMarioBros-v1')
    env = JoypadSpace(env, COMPLEX_MOVEMENT)
    host = "localhost"
    done = False
    network : ComputableNetwork
    id, child_network = getNetworkNovelty(host)
    state = None
    score = 0
    stage = 0
    action = 0  #no op
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
                    "life" : int(info["life"]),
                    "time" : int(game_event_collector.time),
                    "world" : int(info["world"]),
                    "stage" : int(info["stage"]),
                    "yPos" : int(info["y_pos"]),
                    "xPos" : int(info["x_pos"]),
                    "score" : int(game_event_collector.score),
                    "coins" : int(game_event_collector.coins),
                    "mushrooms" : int(game_event_collector.mushrooms),
                    "fireFlowers" : int(game_event_collector.fire_flowers),
                    "flags" : int(game_event_collector.flags),
                    "lifes" : int(game_event_collector.lifes),
                    "stageParts" : int(game_event_collector.stage_parts),
                    "status" : str(info["status"]),
                }, host)
            id, child_network = getNetworkNovelty(host)
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
            1 / 8,
            #channel_axis=2
        )  # * np.random.binomial(1, .25,  state.size)
        network = child_network
        network.input(state / 255.0, actionToNdArray(action))
        network.compute()
        output = network.output()
        # if (score != info["score"]):
        #     framesSinceMaxXChange = 0
        #     score = info["score"]
        if abs(prevX - info["x_pos"]) > 32:
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
        
        if action != last_action or action == 0:
            framesSinceMaxXChange += max(0, 5 - same_action_counter)
            same_action_counter = max(0, same_action_counter - .1)
        else:
            same_action_counter += .2
        last_action = action
        # env.render()

def actionToNdArray(value: int):
    array = np.zeros([1, 12])
    array[0, value] = 1
    return array


if __name__ == '__main__':
    processes: List[mp.Process] = []
    for i in range(2):
        p = mp.Process(target=mario, daemon=True, args=(True,))
        processes.append(p)
        p.start()

    for p in processes:
        p.join()
    # mario()