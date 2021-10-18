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
env = gym_super_mario_bros.make('SuperMarioBrosRandomStages-v1')
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
        connections: List[NeatNetwork.ConnectionLocation] = list(map(lambda c: NeatNetwork.ConnectionLocation(c[0], c[1], c[2], c[3], c[4], c[5], c[6]), data["connections"]))
        nodes: List[NeatNetwork.ConnectionLocation] = list(map(lambda n: NeatNetwork.NodeLocation(n[0], n[1], n[2]), data["nodes"]))
        print(len(connections))
        print(len(nodes))
        try:
            network = NeatNetwork.constructNetwork(nodes, connections, [[60,64], [5,5], [5,5], [5,5], [1,12]])
            requestNetwork = False
        except Exception as e:
            print(e)
            # deadNetwork()
        return (id, network)

def submitScore(info):
    # print(info["stage"])
    requests.post("http://192.168.0.132:8094/score", json={
        "coins": info["coins"],
        "flag_get": bool(info["flag_get"]),
        "life": int(info["life"]),
        "score":info["score"],
        "stage": int(info["stage"]),
        "status": info["status"],
        "time": info["time"],
        "world": int(info["world"]),
        "x_pos": int(info["x_pos"]),
        "y_pos": int(info["y_pos"]),
        "dstatus": int(info["dstatus"]),
        "dworld": int(info["dworld"]),
        "dstage": int(info["dstage"]),
        "dx": int(info["dx"]),
        "dy": int(info["dy"]),
        "dtime": info["dtime"],
        "dlife": int(info["dlife"]),
        "dcoins": info["dcoins"],
        "dscore": info["dscore"],
        "id" : info["id"]
    })

def deadNetwork():
    requests.post("http://"+ host + ":8094/dead", json={
        "id" : id
    })
    
def statusValue(status):
    if (status == "small"): return 0
    elif (status == "tall"): return 1
    else: return 4

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
    stage = 0
    startInfo = None
    idle = False
    while True:
        if done or idle:
            if done or idle:
                state = env.reset()
            info["reward"] = cumulativeReward
            info["id"] = id
            info["dx"] = info["x_pos"] - startInfo["x_pos"]
            info["dy"] = info["y_pos"] - startInfo["y_pos"]
            info["dworld"] = info["world"] - startInfo["world"]
            info["dstage"] = info["stage"] - startInfo["stage"]
            info["dcoins"] = info["coins"] - startInfo["coins"]
            info["dscore"] = info["score"] - startInfo["score"]
            info["dtime"] = info["time"] - startInfo["time"]
            info["dlife"] = info["life"] - startInfo["life"]
            info["dstatus"] = statusValue(info["status"]) - statusValue(startInfo["status"])
            if (info["flag_get"]):
                submitScore(info)
                id, network = getNetwork()
                cumulativeReward = 0
                startInfo = None
            idleCount = 0
            i=0
            maxX=0
            framesSinceMaxXChange = 0
            
            idle=False
        
        state, reward, done, info = env.step(action)
        if (startInfo == None):
            startInfo = info
        if (status != info["status"]):
            idleCount=-60*2
            framesSinceMaxXChange = 0
        if (stage != info["stage"]):
            maxX = 0
            framesSinceMaxXChange =0
            idleCount = 0
        
        status = info["status"]
        stage = info["stage"]
        state = rescale(rgb2gray(state), 1/4,)
        # print(state.shape)
        cumulativeReward+= reward
        network.input(state)
        network.compute()
        output =network.output()
        if (info["x_pos"] > maxX):
            maxX = info["x_pos"]
            framesSinceMaxXChange = 0
        else:
            framesSinceMaxXChange +=1
        if reward == 0:
            idleCount+= 1
        elif idleCount > 0:
            idleCount -=2
        
        if idleCount > 60*6 or reward < -14 or framesSinceMaxXChange > 20* 45:
            if reward < -14:
                info["life"] = info["life"] - 1
            idle=True
        # print(output)
        # print(output.argmax(1))
        # print(output)
        action = output.argmax(1)[0]
        # if (output.item(action) < .5):
        #     action = 0
        # action = min(math.floor(output * len(COMPLEX_MOVEMENT)), len(COMPLEX_MOVEMENT)-1)
        
        i+= 1
        # if (i % 2 == 0):
        env.render()
            # print(state)

    env.close()
mario(env)
