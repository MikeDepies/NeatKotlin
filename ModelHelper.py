from typing import List
# import requests
import httpx
import time
from ActionBehavior import ActionBehavior
from ComputableNetwork import ComputableNetwork, ConnectionLocation, constructNetwork
from HyperNeatDomain import LayerPlane, LayerShape3D
from NeatService import process_model_data

class ModelTestResult:
    model_id : str
    model_scored: bool
    model_available: bool
    model_part_of_generation: bool

    def __init__(self, model_id : str, model_available : bool, model_scored : bool, model_part_of_generation: bool) -> None:
        self.model_id = model_id
        self.model_available = model_available
        self.model_part_of_generation = model_part_of_generation
        self.model_scored = model_scored
# , network_shape : List[List[int]] = [[1, 143], [11, 11], [5, 5], [5, 5], [5, 5], [5, 5], [1, 9]]
class ModelHelper:
    host : str
    controller_id : str
    network_shape : List[List[int]]
    def __init__(self, controller_id :str, host : str) -> None:
        self.host = host
        self.controller_id = controller_id
  

    def send_evaluation_result(self, model_id : str, score : ActionBehavior):
        
        res = httpx.post("http://" + self.host + ":8091/model/score", json={
                "controllerId": self.controller_id,
                "modelId": model_id,
                "score": {
                    "allActions" : score.actions,
                    "recovery" : score.recovery_sets,
                    "kills": score.kills,
                    "damage" : score.damage_actions,
                    "totalDamageDone" : score.total_damage,
                    "totalDistanceTowardOpponent" : score.total_distance_toward_opponent,
                    "playerDied" : score.player_died
                }
            }, timeout=30)
        print("eval send for " + model_id)
        # if not res.ok:
        #     raise Exception("No data for request")
        

    def getNetwork(self, controllerId):
        res = httpx.post("http://" + self.host + ":8091/model/next", json={
            "controllerId": controllerId,
        }, timeout=30)
        if not res.is_success:
            raise Exception("No data for request")
        data = res.json()
        id, builder = process_model_data(data)
        
        # exit()
        return (id, builder)
    
    def randomBest(self):
        requestNetwork = True
        network = None
        print("getting a \"best\" network")
        try:
            res = httpx.post("http://" + self.host + ":8091/model/best",json={
                    "controllerId": self.controller_id,
                }, timeout=30)
            if not res.is_success:
                raise Exception("No data for request")
            data = res.json()
            id, builder = process_model_data(data)
            
            # exit()
            return (id, builder)
        except Exception as e:
            print(e)
            # print("timeout: failed to get " + str(modelId) + " for " + str(controllerId))
            # time.sleep(1)
            return None

