from cmath import inf
from typing import Any, List
from gym.core import Env
from nes_py.wrappers import JoypadSpace
import gym_zelda_1
from gym_zelda_1.actions import MOVEMENT
import numpy as np
import math
import requests
import NeatNetwork
import time
import multiprocessing as mp
from skimage.transform import rescale, resize, downscale_local_mean
import time
# import cv2 as cv


def getNetwork(host: str):
    requestNetwork = True
    network = None
    while requestNetwork:
        res = requests.get("http://" + host + ":8094/model")
        if not res.ok:
            time.sleep(2)
            continue
        data = res.json()
        id: str = data["id"]
        connections: List[NeatNetwork.ConnectionLocation] = list(
            map(
                lambda c: NeatNetwork.ConnectionLocation(
                    c[0], c[1], c[2], c[3], c[4], c[5], c[6]),
                data["connections"]))
        nodes: List[NeatNetwork.ConnectionLocation] = list(
            map(lambda n: NeatNetwork.NodeLocation(n[0], n[1], n[2]),
                data["nodes"]))
        print(len(connections))
        print(len(nodes))
        try:
            network = NeatNetwork.constructNetwork(
                nodes, connections,
                [[60, 64], [5, 5], [5, 5], [5, 5], [5, 5], [1, 301], [1, 20]])
            requestNetwork = False
        except Exception as e:
            print(e)
            # deadNetwork()
        return (id, network)


def submitScore(data, host: str):
    # print(info["stage"])
    requests.post("http://" + host + ":8094/score", json=data)


def deadNetwork(host: str):
    requests.post("http://" + host + ":8094/dead", json={"id": id})


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
        return prev_info["flag_get"] and not info["flag_get"]

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
            self.score += self.game_event_helper.score_collected(info, self.prev_info)
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
                self.time +=1
        self.prev_info = info

def mario2():
        env = gym_zelda_1.make('Zelda1-v0')
        env = JoypadSpace(env, MOVEMENT)
        host = "localhost"
        done = False
        id, network = getNetwork(host)
        state = None
        score = 0
        stage = 0
        action = 0  #no op
        state = env.reset()
        prevX = 0
        prevY = 0
        framesSinceMaxXChange = 0
        framesSinceNewMapLocation = 0
        status = "small"
        startInfo = None
        idle = False
        prev_deaths = 0
        died = 0
        mapLocationsVisited = []
        prev_location = 0
        prev_animation = -1
        prev_item = -1
        time_multiplier = 1
        pauses = 0
        prev_sword = "None"
        prevent_pauses = False
        # game_event_helper = GameEventHelper()
        # game_event_collector = GameEventCollector(game_event_helper, 0)

        while True:
            if (done or idle) and not info["is_scrolling"]:
                
                # death
                submitScore(
                    {
                        "id": id,
                        "xPos" : int(info["x_pos"]),
                        "yPos" : int(info["y_pos"]),
                        "currentLevel" : int(info["current_level"]),
                        "killedEnemies" : int(info["killed_enemies"]),
                        "numberOfDeaths" : int(info["number_of_deaths"]),
                        "sword" : info["sword"],
                        "numberOfBombs" : int(info["number_of_bombs"]),
                        "hasBow" : info["has_bow"],
                        "hasWhistle" : info["has_whistle"],
                        "hasFood" : info["has_food"],
                        "hasMagicRod" : info["has_magic_rod"],
                        "hasRaft" : info["has_raft"],
                        "hasMagicBook" : info["has_magic_book"],
                        "hasStepLadder" : info["has_step_ladder"],
                        "hasMagicKey" : info["has_magic_key"],
                        "hasLetter" : info["has_letter"],
                        "hasPowerBracelet" :info["has_power_bracelet"],
                        "isClockPossessed" : info["is_clock_possessed"],
                        "rupees" : int(info["rupees"]),
                        "keys" : int(info["keys"]),
                        "heartContainers" : int(info["heart_containers"]),
                        "hearts" : float(info["hearts"]),
                        "hasBoomerang" : info["has_boomerang"],
                        "hasMagicBoomerang" : info["has_magic_boomerang"],
                        "hasMagicShield" : info["has_magic_shield"],
                        "maxNumberOfBombs" : int(info["max_number_of_bombs"]),
                        "mapLocation" : int(info["map_location"]),
                    }, host)
                # state = env.reset()
                exit(0)
                id, network = getNetwork(host)
                
                startInfo = None
                prevX = 0
                steps_left = 0
                steps_right = 0
                framesSinceMaxXChange = 0
                framesSinceNewMapLocation = 0
                idle = False
                died = 0
                

            state, reward, done, info = env.step(action)
            
            if (startInfo == None):
                startInfo = info
           
            state = rescale(
                rgb2gray(state),
                1 / 4,
            )  # * np.random.binomial(1, .25,  state.size)

            network.input(state)
            network.compute()
            output = network.output()
            # if (score != info["score"]):
            #     framesSinceMaxXChange = 0
            #     score = info["score"]
            # print(prev_sword)
            if info['map_location'] != prev_location:
                framesSinceNewMapLocation += 300
                if info["sword"] == "None" and prev_location != 0:
                    idle = True
            if info['map_location'] not in mapLocationsVisited:
                mapLocationsVisited.append(info['map_location'])
                framesSinceNewMapLocation = 0
                print("adding visit " + str(info['map_location']))
                if len(mapLocationsVisited) > 3:
                    mapLocationsVisited.pop(0)
            # else:
            #     framesSinceNewMapLocation += 2
            if prev_sword != info["sword"]:
                print("SWORD FOUND!")
                time_multiplier +=1
                framesSinceMaxXChange = 0
                framesSinceNewMapLocation = 0
            if abs(prevX - int(info["x_pos"])) > 16:
                prevX = int(info["x_pos"])
                framesSinceMaxXChange = 0
            elif abs(prevY - int(info["y_pos"])) > 16:
                prevY = int(info["y_pos"])
                framesSinceMaxXChange = 0
            else:
                framesSinceMaxXChange += 1
            framesSinceMaxXChange = max(-10 * 20, framesSinceMaxXChange)
            
            # # print(str(info["x_pos"]) + ", " + str(info["y_pos"]))
            # if prev_item != info['current_item_on_screen']:
            #     print("item" + str(info['current_item_on_screen']))
            
            # if prev_animation != info['link_animation']:
            #     print("animation" + str(info['link_animation']))

            if framesSinceMaxXChange > 20 * 20 * time_multiplier or framesSinceNewMapLocation > 20 * 300 or info["number_of_deaths"] > 0:
                idle = True

            action = output.argmax(1)[0]
            prev_deaths = info['number_of_deaths']
            prev_location = info['map_location']
            prev_animation = info['link_animation']
            prev_item = info['current_item_on_screen']
            prev_sword = info['sword']
            if action == 1:
                print("pause detected")
                pauses +=1
                if pauses > 4:
                    prevent_pauses = True
                if prevent_pauses:
                    action = 0
            # pauses -= .001
            if pauses < 0:
                prevent_pauses = False
                pauses = 0
            # env.render()


if __name__ == '__main__':
    
    while True:
        processes: List[mp.Process] = []
        for i in range(1):
            p = mp.Process(target=mario2, daemon=True)
            processes.append(p)
            p.start()

        for p in processes:
            p.join()
    # mario()