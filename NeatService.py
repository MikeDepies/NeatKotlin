from httpx import post, get
from dataclasses import dataclass
from NeatDomain import NeatModel, parse_neat_model


class NeatService:
    host: str

    def getBestModel(self, controller_id : int) -> NeatModel:
        response = post(self.host + "/stream/next_model", json={
            "controllerId": controller_id
        },)
        data = response.json()
        return parse_neat_model(data)

    