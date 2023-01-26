from typing import List
# import requests
import httpx
import time
from ActionBehavior import ActionBehavior
from HyperNeatDomain import LayerPlane, LayerShape3D
from NeatService import process_model_data, process_model_data_mcc
from dataclasses import dataclass
@dataclass
class EvalResult:
    id : str
    satisfy: bool

class ModelHelperMCC:
    host : str
    
    def __init__(self, host : str) -> None:
        self.host = host
  

    def send_evaluation_result(self, result : EvalResult):
        
        res = httpx.post("http://" + self.host + ":8091/model/score", json={
                "id": result.id,
                "satisfyMC": result.satisfy
            }, timeout=30)
        print("eval send for " + str(result))
        # if not res.ok:
        #     raise Exception("No data for request")
        

    def getNetworks(self):
        res = httpx.get("http://" + self.host + ":8091/model/next", timeout=3)
        if not res.is_success:
            raise Exception("No data for request")
        data = res.json()
        agent_controller_id : int = data["agentControllerId"]
        child_controller_id : int = data["childControllerId"]
        id, agent, child = process_model_data_mcc(data)
    
        return id, agent, child, agent_controller_id, child_controller_id
    
    def randomBest(self, controller_id : int):
        requestNetwork = True
        network = None
        print("getting a \"best\" network")
        
        res = httpx.post("http://" + self.host + ":8091/model/best",json={
                "controllerId": controller_id,
            }, timeout=30)
        if not res.is_success:
            raise Exception("No data for request")
        data = res.json()
        id, builder = process_model_data(data)
        
        # exit()
        return (id, builder)
        

