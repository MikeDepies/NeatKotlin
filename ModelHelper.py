from typing import List
# import requests
import httpx
import time
from ActionBehavior import ActionBehavior
from NeatNetwork import ComputableNetwork, ConnectionLocation, NodeLocation, constructNetwork
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

class ModelHelper:
    host : str
    controller_id : str
    network_shape : List[List[int]]
    def __init__(self, controller_id :str, host : str, network_shape : List[List[int]] = [[1, 133], [5,5], [5, 5], [5, 5], [5, 5], [5, 5], [1, 9]]) -> None:
        self.host = host
        self.controller_id = controller_id
        self.network_shape = network_shape
    def getModels(self) -> List[str]:
        res = httpx.post("http://" + self.host + ":8091/models", json={
                "controllerId": self.controller_id,
            }, timeout=.5)
        # if not res.ok:
        #     raise Exception("No data for request")
        
        data = res.json()
        if data["ready"]:
            print("Model ID LIST ACQUIRED: " + str(len(data["modelIds"])))
            return data["modelIds"]
        else:
            # print("sleeping 1 second")
            # time.sleep(1)
            return []

    def testModelId(self, model_id : str) -> ModelTestResult:
        res = httpx.post("http://" + self.host + ":8091/model/check", json={
                "controllerId": self.controller_id,
                "modelId": model_id,
            }, timeout=30)
        
        data = res.json()
        return ModelTestResult(model_id, data["available"], data["scored"], data["valid"])

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
        

    def getNetwork(self, controllerId, modelId) -> ComputableNetwork:
        requestNetwork = True
        network = None
        print("get network")
        try:
            res = httpx.post("http://" + self.host + ":8091/model/request", json={
                "controllerId": controllerId,
                "modelId": modelId,
                
            }, timeout=10)
            if not res.is_success:
                raise Exception("No data for request")
            data = res.json()
            connections: List[ConnectionLocation] = list(map(lambda c: ConnectionLocation(
                c[0], c[1], c[2], c[3], c[4], c[5], c[6]), data["connections"]))
            nodes: List[ConnectionLocation] = list(
                map(lambda n: NodeLocation(n[0], n[1], n[2]), data["nodes"]))
            print(len(connections))
            return constructNetwork(nodes, connections, self.network_shape)
        except Exception as e:
            print(e)
            print("timeout: failed to get " + str(modelId) + " for " + str(controllerId))
            # time.sleep(1)
            return None
        requestNetwork = False
        # requests.post("http://" + host + ":8091/start", json={
        #         "controllerId": controllerId,
        #         "modelId": modelId,
                
        #     }, timeout=2)
        return network
    
    def randomBest(self) -> ComputableNetwork:
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
            connections: List[ConnectionLocation] = list(map(lambda c: ConnectionLocation(
                c[0], c[1], c[2], c[3], c[4], c[5], c[6]), data["connections"]))
            nodes: List[ConnectionLocation] = list(
                map(lambda n: NodeLocation(n[0], n[1], n[2]), data["nodes"]))
            print(len(connections))
            print(data["id"])
            return constructNetwork(nodes, connections, self.network_shape)
        except Exception as e:
            print(e)
            # print("timeout: failed to get " + str(modelId) + " for " + str(controllerId))
            # time.sleep(1)
            return None
