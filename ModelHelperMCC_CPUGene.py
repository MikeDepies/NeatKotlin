from typing import List
# import requests
import httpx
import time
from ActionBehavior import ActionBehavior
from HyperNeatDomain import LayerPlane, LayerShape3D
from NeatService import process_model_data, process_model_data_mcc_cpu_gene
from dataclasses import dataclass
import melee
@dataclass
class EvalResultCPU:
    population_type: str
    agent_id : str
    environment_id: str
    satisfy: bool
    dead: bool

class ModelHelperMCC_CPUGene:
    host : str
    
    def __init__(self, host : str) -> None:
        self.host = host
  

    def send_evaluation_result(self, result : EvalResultCPU):
        
        res = httpx.post("http://" + self.host + ":8091/model/score", json={
                "agentId": result.agent_id,
                "environmentId": result.environment_id,
                "type" :result.population_type,
                "satisfyMC": result.satisfy,
                "dead": result.dead
            }, timeout=30)
        print("eval send for " + str(result))
        # if not res.ok:
        #     raise Exception("No data for request")
        

    def getNetworks(self):
        res = httpx.get("http://" + self.host + ":8091/model/next", timeout=3)
        if not res.is_success:
            raise Exception("No data for request")
        data = res.json()
        agent_id = data["agentId"]
        environment_id = data["environmentId"]
        population_type = data["type"]
        id, agent, cpu_gene = process_model_data_mcc_cpu_gene(data)
    
        return population_type, agent_id, environment_id, agent, cpu_gene
    
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
        

