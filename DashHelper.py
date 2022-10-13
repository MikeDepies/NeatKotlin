from httpx import post


class DashHelper:
    controller_id : int

    def __init__(self, controller_id) -> None:
        self.controller_id = controller_id
        
    def updateModel(self, model_id : str):
        post("http://localhost:3000/api/model",json={
                    "controllerId": self.controller_id,
                    "modelId" : model_id
                }, timeout=.5)
        

    def updateKill(self):
        post("http://localhost:3000/api/stat/kill",json={
                    "controllerId": self.controller_id,
                }, timeout=.5)
        

    def updateDeath(self):
        post("http://localhost:3000/api/stat/death",json={
                    "controllerId": self.controller_id,
                }, timeout=.5)
        
    
    def updateWin(self):
        post("http://localhost:3000/api/stat/win",json={
                    "controllerId": self.controller_id,
                }, timeout=.5)
        
    
    def updateLoss(self):
        post("http://localhost:3000/api/stat/loss",json={
                    "controllerId": self.controller_id,
                }, timeout=.5)