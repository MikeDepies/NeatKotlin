from typing import Any
import melee
from dataclasses import dataclass


@dataclass
class PlayerConfiguration:
    cpu_level : int
    character : melee.Character

@dataclass
class EvaluatorConfiguration:
    attack_time : int
    max_time : int
    action_limit : int

@dataclass
class Configuration:
    stage : melee.Stage
    player_1 : PlayerConfiguration
    player_2 : PlayerConfiguration
    evaluator : EvaluatorConfiguration


def parseCharacter(character_string : str):
    
    character_table = {
        "mario" : melee.Character.MARIO,
        "fox" : melee.Character.FOX,
        "falco" : melee.Character.FALCO,
        "captainfalcon" : melee.Character.CPTFALCON,
        "marth" : melee.Character.MARTH,
        "pikachu" : melee.Character.PIKACHU,
        "link" : melee.Character.LINK,
        "yoshi" : melee.Character.YOSHI,
        "doctormario" : melee.Character.DOC,
        "donkeykong" : melee.Character.DK,
        "bowser" : melee.Character.BOWSER,
        "gameandwatch" : melee.Character.GAMEANDWATCH,
        "gannondorf" : melee.Character.GANONDORF,
        "jigglypuff" : melee.Character.JIGGLYPUFF,
        "kirby" : melee.Character.KIRBY,
        "luigi" : melee.Character.LUIGI,
        "mewtwo" : melee.Character.MEWTWO,
        "ness" : melee.Character.NESS,
        "peach" : melee.Character.PEACH,
        "pichu" : melee.Character.PICHU,
        "roy" : melee.Character.ROY,
        "sheik" : melee.Character.SHEIK,
        "younglink" : melee.Character.YLINK
    }
    return character_table[character_string.lower()]
    
def parseStage(stage_string : str):
    stage_table = {
        "finaldestination" : melee.Stage.FINAL_DESTINATION,
        "battlefield" : melee.Stage.BATTLEFIELD,
        "dreamland" : melee.Stage.DREAMLAND,
        "pokemonstadium" : melee.Stage.POKEMON_STADIUM
    }
    return stage_table[stage_string.lower()]


def processPlayer(data : Any):
    return PlayerConfiguration(int(data["cpuLevel"]), parseCharacter(data["character"]))

def processEvaluatorConfiguration(data : Any):
    return EvaluatorConfiguration(int(data["attackTime"]), int(data["maxTime"]), int(data["actionLimit"]))


def processConfiguration(data : Any):
    stage = parseStage(data["stage"])
    player_1 = processPlayer(data["player1"])
    player_2 = processPlayer(data["player2"])
    evaluator_configuration = processEvaluatorConfiguration(data["evaluatorSettings"])
    return Configuration(stage, player_1, player_2, evaluator_configuration)
