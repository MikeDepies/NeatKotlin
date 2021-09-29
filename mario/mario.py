from typing import List
from gym.core import Env
from nes_py.wrappers import JoypadSpace
import gym_super_mario_bros
import asyncio
import numpy as np
import math
import requests
import NeatNetwork
import time
from skimage.transform import rescale, resize, downscale_local_mean
import time
# import cv2 as cv
from gym_super_mario_bros.actions import COMPLEX_MOVEMENT
env = gym_super_mario_bros.make('SuperMarioBros-v1')
env = JoypadSpace(env, COMPLEX_MOVEMENT)
host = "192.168.0.132"

def getNetwork():
    requestNetwork = True
    network = None
    while requestNetwork:
        res = requests.get("http://"+ host + ":8094/model")
        if not res.ok:
            time.sleep(2)
            continue
        data = res.json()
        id : str = data["id"]
        connections: List[NeatNetwork.ConnectionLocation] = list(map(lambda c: NeatNetwork.ConnectionLocation(**c), data["connections"]))
        nodes: List[NeatNetwork.ConnectionLocation] = list(map(lambda n: NeatNetwork.NodeLocation(**n), data["nodes"]))
        print(len(connections))
        print(len(nodes))
        try:
            network = NeatNetwork.constructNetwork(nodes, connections)
            requestNetwork = False
        except:
            print("failed to build")
            # deadNetwork()
        return (id, network)

def submitScore(info):
    # print(info["stage"])
    requests.post("http://192.168.0.132:8094/score", json={
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
        "id" : info["id"]
    })

def deadNetwork():
    requests.post("http://"+ host + ":8094/dead", json={
        "id" : id
    })

def rgb2gray(rgb):
    return np.dot(rgb[...,:3], [0.2989, 0.5870, 0.1140])

def mario(env: Env):
    uri = "ws://"+ host + ":8090/ws"
    done = False
    id, network = getNetwork()
    # network.write()
    state = None
    action = 0 #no op
    cumulativeReward = 0
    state = env.reset()
    idleCount = 0
    i =0
    maxX=0
    framesSinceMaxXChange = 0
    status = "small"
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
            maxX=0
            framesSinceMaxXChange = 0
        
        state, reward, done, info = env.step(action)
        
        if (status != info["status"]):
            idleCount = 0
        status = info["status"]
        state = rescale(rgb2gray(state), 1/16,)
        # print(state.shape)
        cumulativeReward+= reward
        network.input(state)
        network.compute()
        output =network.output()[0]
        if (info["x_pos"] > maxX):
            maxX = info["x_pos"]
            framesSinceMaxXChange = 0
        else:
            framesSinceMaxXChange +=1
        if reward == 0:
            idleCount+= 1
        else:
            idleCount -=4
        if idleCount < 0:
            idleCount = 0
        if idleCount > 60*2 or reward < -14 or framesSinceMaxXChange > 20* 25:
            done=True
        # print(output)
        action = min(math.floor(output * len(COMPLEX_MOVEMENT)), 6)
        i+= 1
        if (i % 2 == 0):
            env.render()
            # print(state)

    env.close()
mario(env)
