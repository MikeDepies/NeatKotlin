from typing import List
from gym.core import Env
from nes_py.wrappers import JoypadSpace
import gym_super_mario_bros
import asyncio
import numpy as np
import math
import requests
import NeatNetwork
from skimage.transform import rescale, resize, downscale_local_mean
# import cv2 as cv
from gym_super_mario_bros.actions import SIMPLE_MOVEMENT
env = gym_super_mario_bros.make('SuperMarioBros-v1')
env = JoypadSpace(env, SIMPLE_MOVEMENT)


def getNetwork():
    requestNetwork = True
    network = None
    while requestNetwork:
        res = requests.get("http://192.168.1.132:8094/model")
        if not res.ok:
            continue
        data = res.json()
        id : int = data["id"]
        connections: List[NeatNetwork.ConnectionLocation] = list(map(lambda c: NeatNetwork.ConnectionLocation(**c), data["connections"]))
        nodes: List[NeatNetwork.ConnectionLocation] = list(map(lambda n: NeatNetwork.NodeLocation(**n), data["nodes"]))
        print(len(connections))
        print(len(nodes))
        try:
            network = NeatNetwork.constructNetwork(nodes, connections)
            requestNetwork = False
        except:
            print("failed to build")
            deadNetwork()
        return (id, network)

def submitScore(info):
    # print(info["stage"])
    requests.post("http://192.168.1.132:8094/score", json={
        "coins": info["coins"],
        "flag_get": info["flag_get"],
        "life": int(info["life"]),
        "score":info["score"],
        "stage": int(info["stage"]),
        "status": info["status"],
        "time": info["time"],
        "world": int(info["world"]),
        "x_pos": int(info["x_pos"]),
        "y_pos": int(info["y_pos"]),
        "id" : int(info["id"])
    })

def deadNetwork():
    requests.post("http://192.168.1.132:8094/dead", json={
        "id" : id
    })

def rgb2gray(rgb):
    return np.dot(rgb[...,:3], [0.2989, 0.5870, 0.1140])

def mario(env: Env):
    done = False
    id, network = getNetwork()
    # network.write()
    state = None
    action = 0 #no op
    cumulativeReward = 0
    state = env.reset()
    idleCount = 0
    i =0
    while True:
        if done:
            state = env.reset()
            info["reward"] = reward
            info["id"] = id
            submitScore(info)
            id, network = getNetwork()
            cumulativeReward = 0
            idleCount = 0
            i=0
        
        state, reward, done, info = env.step(action)
        
        state = rescale(rgb2gray(state), 1/16,)
        cumulativeReward+= reward
        network.input(state)
        network.compute()
        output =network.output()[0]
        if reward == 0:
            idleCount+= 1
        else:
            idleCount -=4
        if idleCount < 0:
            idleCount = 0
        if idleCount > 60 or reward < -14:
            done=True
        # print(output)
        action = min(math.floor(output * 7), 6)
        i+= 1
        # if (i % 20 == 0):
        #     env.render()
            # print(state)

    env.close()
mario(env)
