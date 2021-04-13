#!/usr/bin/python3

import argparse
from asyncio.tasks import sleep
from asyncio.windows_events import NULL
import json
import signal
import sys
from typing import Dict
import melee
import random
from melee import gamestate
from melee.enums import Button
from melee.gamestate import GameState, PlayerState
from melee.stages import randall_position
import numpy as np
import websockets
import asyncio
import threading
import math
import random
from websockets.client import WebSocketClientProtocol
# This example program demonstrates how to use the Melee API to run a console,
#   setup controllers, and send button presses over to a console


class NumpyEncoder(json.JSONEncoder):
    """ Special json encoder for numpy types """

    def default(self, obj):
        if isinstance(obj, np.integer):
            value = int(obj)
            return 0 if math.isnan(value) else value
        elif isinstance(obj, np.floating):
            value = float(obj)
            if math.isnan(value):
                return 0
            else:
                return value
        elif isinstance(obj, np.ndarray):
            return obj.tolist()
        return json.JSONEncoder.default(self, obj)


def check_port(value):
    ivalue = int(value)
    if ivalue < 1 or ivalue > 4:
        raise argparse.ArgumentTypeError("%s is an invalid controller port. \
                                         Must be 1, 2, 3, or 4." % value)
    return ivalue


parser = argparse.ArgumentParser(description='Example of libmelee in action')
parser.add_argument('--port', '-p', type=check_port,
                    help='The controller port (1-4) your AI will play on',
                    default=2)
parser.add_argument('--opponent', '-o', type=check_port,
                    help='The controller port (1-4) the opponent will play on',
                    default=1)
parser.add_argument('--debug', '-d', action='store_true',
                    help='Debug mode. Creates a CSV of all game states')
parser.add_argument('--address', '-a', default="127.0.0.1",
                    help='IP address of Slippi/Wii')
parser.add_argument('--dolphin_executable_path', '-e', default=None,
                    help='The directory where dolphin is')
parser.add_argument('--connect_code', '-t', default="",
                    help='Direct connect code to connect to in Slippi Online')
parser.add_argument('--iso', default=None, type=str,
                    help='Path to melee iso.')

args = parser.parse_args()

# This logger object is useful for retroactively debugging issues in your bot
#   You can write things to it each frame, and it will create a CSV file describing the match
log = None
if args.debug:
    log = melee.Logger()

# Create our Console object.
#   This will be one of the primary objects that we will interface with.
#   The Console represents the virtual or hardware system Melee is playing on.
#   Through this object, we can get "GameState" objects per-frame so that your
#       bot can actually "see" what's happening in the game
console = melee.Console(path=args.dolphin_executable_path,
                        slippi_address=args.address,
                        logger=log)

# Create our Controller object
#   The controller is the second primary object your bot will interact with
#   Your controller is your way of sending button presses to the game, whether
#   virtual or physical.
controller = melee.Controller(console=console,
                              port=args.port,
                              type=melee.ControllerType.STANDARD)
print("Player port: " + str(args.opponent))
controller_opponent = melee.Controller(console=console,
                                       port=args.opponent,
                                       type=melee.ControllerType.STANDARD)

# This isn't necessary, but makes it so that Dolphin will get killed when you ^C
fd = melee.framedata.FrameData()


def signal_handler(sig, frame):
    console.stop()
    # Session.ws.send(json.dumps(createMessage("simulation.pause", {})))
    if args.debug:
        log.writelog()
        print("")  # because the ^C will be on the terminal
        print("Log file created: " + log.filename)
    print("Shutting down cleanly...")
    sys.exit(0)


signal.signal(signal.SIGINT, signal_handler)

# Run the console
console.run(iso_path=args.iso)

# Connect to the console
print("Connecting to console... ")
if not console.connect():
    print("ERROR: Failed to connect to the console.")
    sys.exit(-1)
print("Console connected")

# Plug our controller in
#   Due to how named pipes work, this has to come AFTER running dolphin
#   NOTE: If you're loading a movie file, don't connect the controller,
#   dolphin will hang waiting for input and never receive it
print("Connecting controller to console...")
if not controller.connect():
    print("ERROR: Failed to connect the controller.")
    sys.exit(-1)
print("Controller connected")

print("Connecting controller to console...")
if not controller_opponent.connect():
    print("ERROR: Failed to connect the controller.")
    sys.exit(-1)
print("Controller connected")


class Session:
    framesOnCharacterSelect = 0
    ws: WebSocketClientProtocol or None = None
    simulationRunning = False
    menuLoadFirstFrame = True
    gamestate: GameState or None = None
    lastStockAi = 4
    lastStockOpponent = 4
    lastGamestate: GameState or None = None
    cpu_level = random.randint(1, 9)
    cpu_character = melee.Character.FOX
    ai_character = melee.Character.SAMUS
    reassign_characters = True
    character_pool = [melee.Character.FOX, melee.Character.SAMUS, melee.Character.FALCO, melee.Character.MARTH,
                      melee.Character.CPTFALCON, melee.Character.PEACH, melee.Character.PIKACHU, melee.Character.ZELDA, melee.Character.GANONDORF, melee.Character.JIGGLYPUFF]


def processMessage(message: Dict, controller: melee.Controller):
    if (message["a"] == True):
        controller.press_button(Button.BUTTON_A)
    else:
        controller.release_button(Button.BUTTON_A)
    if (message["b"] == True):
        controller.press_button(Button.BUTTON_B)
    else:
        controller.release_button(Button.BUTTON_B)
    if (message["y"] == True):
        controller.press_button(Button.BUTTON_Y)
    else:
        controller.release_button(Button.BUTTON_Y)

    if (message["z"] == True):
        controller.press_button(Button.BUTTON_Z)
    else:
        controller.release_button(Button.BUTTON_Z)

    controller.tilt_analog(
        Button.BUTTON_MAIN, message["mainStickX"], message["mainStickY"])
    controller.tilt_analog(
        Button.BUTTON_C, message["cStickX"], message["cStickY"])
    controller.press_shoulder(Button.BUTTON_L, message["leftShoulder"])
    if (message["leftShoulder"] >= 1):
        controller.press_button(Button.BUTTON_L)
    else:
        controller.release_button(Button.BUTTON_L)
    # controller.press_shoulder(Button.BUTTON_R, message["rightShoulder"])
    
    if (not Session.menuLoadFirstFrame):
        controller.flush()


def actionData(gameState: GameState, port: int):
    player: PlayerState = gameState.players[port]
    rangeForward = fd.range_forward(
        player.character, player.action, player.action_frame)
    rangeBackward = fd.range_backward(
        player.character, player.action, player.action_frame)
    if (math.isnan(rangeBackward)):
        rangeBackward = 0
    if (math.isnan(rangeForward)):
        rangeForward = 0

    return {
        "action": player.action.value,
        "isAttack": bool(fd.is_attack(character=player.character, action=player.action)),
        "isGrab": bool(fd.is_grab(player.character, player.action)),
        "isBMove": bool(fd.is_bmove(player.character, player.action)),
        "isShield": bool(fd.is_shield(player.action)),
        "rangeBackward": rangeBackward,
        "rangeForward": rangeForward,
        "hitBoxCount": fd.hitbox_count(player.character, player.action),
        "attackState": fd.attack_state(player.character, player.action, player.action_frame).value,
        "actionFrame": player.action_frame
    }


def environmentalCollisionBox(player: PlayerState):
    return {
        "left": {
            "x": player.ecb_left[0],
            "y": player.ecb_left[1]
        },
        "top": {
            "x": player.ecb_top[0],
            "y": player.ecb_top[1]
        },
        "right": {
            "x": player.ecb_right[0],
            "y": player.ecb_right[1]
        },
        "bottom": {
            "x": player.ecb_bottom[0],
            "y": player.ecb_bottom[1]
        }
    }


def playerData(gameState: GameState, port: int):
    player: PlayerState = gameState.players[port]
    x = 0
    y = 0

    if gameState.menu_state in [melee.Menu.CHARACTER_SELECT]:
        x = player.cursor.x
        y = player.cursor.y
    else:
        x = player.position.x
        y = player.position.y
    return {
        "character": player.character.value,
        "stock": player.stock,
        "x": x,
        "y": y,
        "speedAirX": player.speed_air_x_self,
        "speedGroundX": player.speed_ground_x_self,
        "speedXAttack": player.speed_x_attack,
        "speedYAttack": player.speed_y_attack,
        "speedY": player.speed_y_self,
        "percent": player.percent,
        "facingRight": bool(player.facing),
        "ecb": environmentalCollisionBox(player),
        "onGround": bool(player.on_ground),
        "hitStun": bool(player.hitlag),
        "invulnerable": bool(player.invulnerable),
        "offStage": bool(player.off_stage)
    }


def stageData(gameState: GameState):
    blastzones: tuple[float, float, float,
                      float] = melee.stages.BLASTZONES[gameState.stage]
    leftPlatform: tuple[float, float,
                        float] = melee.stages.left_platform_position(gameState)
    # print(leftPlatform)
    rightPlatform: tuple[float, float,
                         float] = melee.stages.right_platform_position(gameState)
    topPlatform: tuple[float, float,
                       float] = melee.stages.top_platform_position(gameState)
    randall = randall_position(gameState.frame)
    edge = melee.stages.EDGE_GROUND_POSITION[gameState.stage]
    # print(edge)
    data = {
        "stage": gameState.stage.value,
        "leftEdge": edge * -1,
        "rightEdge": edge,
        "randall": {
            "height": randall[0],
            "left": randall[1],
            "right": randall[2],
        },
        "blastzone": {
            "left": blastzones[0],
            "right": blastzones[1],
            "top": blastzones[2],
            "bottom": blastzones[3],
        },
    }
    if (leftPlatform is not None):
        data["platformLeft"] = {
            "height": leftPlatform[0],
            "left": leftPlatform[1],
            "right": leftPlatform[2]
        }
    else:
        data["platformLeft"] = {
            "height": 0,
            "left": 0,
            "right": 0
        }
    if (topPlatform is not None):
        data["platformTop"] = {
            "height": topPlatform[0],
            "left": topPlatform[1],
            "right": topPlatform[2]
        }
    else:
        data["platformTop"] = {
            "height": 0,
            "left": 0,
            "right": 0
        }
    if (rightPlatform is not None):
        data["platformRight"] = {
            "height": rightPlatform[0],
            "left": rightPlatform[1],
            "right": rightPlatform[2]
        }
    else:
        data["platformRight"] = {
            "height": 0,
            "left": 0,
            "right": 0
        }
    return data


def frameData(gameState: GameState):
    return {
        "player1": playerData(gameState, args.port),
        "player2": playerData(gameState, args.opponent),
        "action1": actionData(gameState, args.port),
        "action2": actionData(gameState, args.opponent),
        "stage": stageData(gameState),
        "distance": gameState.distance,
        "frame": gameState.frame
    }


async def handle_message():
    uri = "ws://localhost:8090/ws"
    async with websockets.connect(uri) as websocket:

        try:
            await websocket.send(json.dumps({
                "clientId": "pythonAgent"
            }))
            Session.ws = websocket
            while True:
                got_back = await websocket.recv()
                if (Session.gamestate is not None and Session.gamestate.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]):
                    msg: Dict = json.loads(got_back)
                    if (msg["data"]["controllerId"] == 0):
                        processMessage(msg["data"], controller)
                    elif (msg["data"]["controllerId"] == 1):
                        processMessage(msg["data"], controller_opponent)
                # else:
                #     controller.release_all()
                #     controller.flush()
        except websockets.exceptions.ConnectionClosedError as cce:
            print('Connection closed!')
        except KeyboardInterrupt as ki:
            print('Ending...')
        finally:
            console.stop()


def createMessage(topic: str, data):
    return {
        "topic": topic,
        "playerRef": "guest",
        "data": data
    }


async def console_loop():
    # Main loop
    costume = 0
    while True:
        # await asyncio.sleep(.001)
        # "step" to the next frame
        gamestate = console.step()
        Session.gamestate = gamestate
        if gamestate is None or Session.ws is None:
            await asyncio.sleep(.001)
            continue

        # The console object keeps track of how long your bot is taking to process frames
        #   And can warn you if it's taking too long
        if console.processingtime * 1000 > 50:
            print("WARNING: Last frame took " +
                  str(console.processingtime*1000) + "ms to process.")

        # What menu are we in?
        if gamestate.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:
            await asyncio.sleep(.00001)
            if not Session.reassign_characters:
                Session.reassign_characters = True
                Session.menuLoadFirstFrame = False
            
            ai: PlayerState = gamestate.players[args.port]
            if ai.cpu_level != 0 and Session.simulationRunning:
                print("AI was swapped out for CPU! Pausing Simulation Server")
                await Session.ws.send(json.dumps(createMessage("simulation.pause", {})))
                Session.simulationRunning = False

            if ai.cpu_level == 0 and not Session.simulationRunning:
                print("Server and simulation now resumed.")
                await Session.ws.send(json.dumps(createMessage("simulation.resume", {})))
                Session.simulationRunning = True
            if gamestate.frame == 0:
                print("new game!")
                await Session.ws.send(json.dumps(createMessage("simulation.reset.game", {})))
                newGame = False

            if Session.ws is not None and Session.simulationRunning:
                # if (counter % 10 == 0):
                # print("sending data")
                messageString = json.dumps(createMessage(
                    "simulation.frame.update", frameData(gamestate)), cls=NumpyEncoder)
                # print(messageString)
                await Session.ws.send(messageString)
                # counter += 1
                player: PlayerState = gamestate.players[args.port]
                player2: PlayerState = gamestate.players[args.opponent]
                # print("enemy stocks left: " + str(player2.stock))
                Session.lastStockAi = player.stock
                Session.lastStockOpponent = player2.stock
                Session.lastGamestate = gamestate
                # if (Session.lastStockAi == 0 or Session.lastStockOpponent == 0):
                    # messageString = json.dumps(createMessage(
                    #     "simulation.frame.update", frameData(gamestate)), cls=NumpyEncoder)
                    # await Session.ws.send(messageString)
                    # print("stocks left: " + str(Session.lastStockAi))
                    # print("enemy stocks left: " +
                    #       str(Session.lastStockOpponent))
                    # controller.release_all()
                    # controller_opponent.release_all()
                    # controller.flush()
                    # controller_opponent.flush()
                # NOTE: This is where your AI does all of its stuff!
                # This line will get hit once per frame, so here is where you read
                #   in the gamestate and decide what buttons to push on the controller
                # melee.techskill.multishine(
                #     ai_state=gamestate.players[discovered_port], controller=controller)
            else:
                # If the discovered port was unsure, reroll our costume for next time
                costume = random.randint(0, 4)

        else:
            if Session.reassign_characters:
                Session.ai_character = random.choice(Session.character_pool)
                Session.cpu_character = random.choice(Session.character_pool)
                Session.cpu_level = random.randint(1, 9)
                Session.reassign_characters = False
                # controller_opponent.release_all()
            if not Session.menuLoadFirstFrame:
                Session.menuLoadFirstFrame = True
                # print("tried to flush controller")
                # controller.release_all()
                # controller.flush()
                # controller_opponent.release_all()
                # controller_opponent.flush()
            # print("in menu")
            # if (gamestate.menu_state not in [melee.Menu.CHARACTER_SELECT]):
            # if gamestate.menu_selection in [melee.Menu.STAGE_SELECT]:
            # melee.MenuHelper.choose_character(Session.cpu_character, gamestate, controller_opponent)
            # melee.MenuHelper.choose_character(Session.cpu_character, gamestate, controller_opponent)
            melee.MenuHelper.menu_helper_simple(gamestate,
                                                controller,
                                                melee.Character.PIKACHU,
                                                melee.Stage.FINAL_DESTINATION,
                                                args.connect_code,
                                                costume=2,
                                                autostart=False,
                                                swag=False,
                                                cpu_level=0)
            melee.MenuHelper.menu_helper_simple(gamestate,
                                                controller_opponent,
                                                Session.cpu_character,
                                                melee.Stage.FINAL_DESTINATION,
                                                args.connect_code,
                                                costume=2,
                                                autostart=True,
                                                swag=False,
                                                cpu_level=0)
            
            # else:
            #     mh.choose_character(character=melee.Character.FOX, gamestate=gamestate, controller=controller,swag=True)
            counter = 0
            resetCounter = 0
        # if log:
        #     log.logframe(gamestate)
        #     log.writeframe()

loop = asyncio.get_event_loop()


def restartGame():
    if not Session.simulationRunning and gamestate.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:
        if resetCounter == 0:
            print("release all buttons")
            controller.release_all()
            resetCounter = 1
        elif resetCounter == 1:
            print("press start to pause")
            controller.press_button(Button.BUTTON_START)
            resetCounter = 2
        elif resetCounter == 2:
            print("release start")
            controller.release_all()
            resetCounter = 3
        elif resetCounter == 3:
            print("press buttons to exit match")
            controller.press_button(Button.BUTTON_START)
            controller.press_button(Button.BUTTON_L)
            controller.press_button(Button.BUTTON_R)
            controller.press_button(Button.BUTTON_A)
            resetCounter = 4
        controller.flush()
    # continue


async def test1(value: str):
    while True:
        print(f'message: {value}')
        await asyncio.sleep(.1)

console.step()
#
# console_loop()
# asyncio.ensure_future(test1("test"))
asyncio.ensure_future(console_loop())
asyncio.ensure_future(handle_message())
try:
    loop.run_forever()
finally:
    console.stop()
    # main.start()
    # loop.run_until_complete(handle_message())
    # main.join()
