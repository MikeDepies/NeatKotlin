from cmath import inf
from typing import Any, Dict, List
from gym.core import Env
from nes_py.wrappers import JoypadSpace
import gym_super_mario_bros
import asyncio
import numpy as np
import math
import requests
from httpx import get


import time
import multiprocessing as mp
from skimage.transform import rescale, resize, downscale_local_mean
import time
# import cv2 as cv
from gym_super_mario_bros.actions import COMPLEX_MOVEMENT
from dataclasses import dataclass
from dacite import from_dict

from ComputableNetwork import ComputableNetwork
from NeatService import process_model_data



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

def get_network_novelty(host: str, port : int):
    res = get("http://" + host + ":" + str(port) + "/model/request", timeout=100)
    if not res.is_success:
        raise Exception("No data for request")
    data = res.json()
    id, builder = process_model_data(data)
    network = builder.create_ndarrays()
    return id, network

def marioNovelty(queue : mp.Queue):
    env = gym_super_mario_bros.make('SuperMarioBros-v1')
    env = JoypadSpace(env, COMPLEX_MOVEMENT)
    host = "localhost"
    port = 8095
    done = False
    network : ComputableNetwork
    # id, child_network = get_network_novelty(host, port)
    id, child_network = queue.get()
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
            1 / 8,
            #channel_axis=2
        )  # * np.random.binomial(1, .25,  state.size)
        network = child_network
        network.input(state / 255.0)
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

        if framesSinceMaxXChange > 30 * 20 or reward < -14:
            idle = True

        action = 11 - output.argmax(1)[0]
        # print(output)
        # print(output.shape)
        # print(action)
        
        if action != last_action or action == 0:
            framesSinceMaxXChange += max(0, 5 - same_action_counter)
            same_action_counter = max(0, same_action_counter - .1)
        else:
            same_action_counter += .2
        last_action = action
        env.render()

def actionToNdArray(value: int):
    array = np.zeros([1, 12])
    array[0, value] = 1
    return array

def queueNetworks(queue : mp.Queue):
    host = "localhost"
    port = 8095
    while True:
        try:
            queue.put(get_network_novelty(host, port))
        except:
            print("failed to get network...")
    pass

if __name__ == '__main__':
    mgr = mp.Manager()
    # ns = mgr.Namespace()
    # host = "localhost"
    # port = 8095
    process_num = 2
    queue = mgr.Queue(process_num * 2)
    processes: List[mp.Process] = []
    
    for i in range(process_num):
        p = mp.Process(target=marioNovelty, daemon=True, args=(queue,))
        processes.append(p)
        p.start()
        p = mp.Process(target=queueNetworks, daemon=True, args=(queue,))
        processes.append(p)
        p.start()

    for p in processes:
        p.join()
    # mario()