import math
from typing import Dict

import melee
from numpy import ndarray

from melee.enums import Button

from ComputableNetwork import ComputableNetwork

class ControllerHelper:
    def processMessage(self, message: Dict, controller: melee.Controller):

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
        if (message["leftShoulder"] >= .9):
            controller.press_button(Button.BUTTON_L)
        else:
            controller.release_button(Button.BUTTON_L)
        # controller.press_shoulder(Button.BUTTON_R, message["rightShoulder"])

        # controller.flush()
    
    def clamp(self, value : float, min_value : float = -4, max_value: float = 4):
        return max(min_value, min(max_value, value))

    def processAnalog(self, analogOutput : ndarray):
        maxAnalog = analogOutput.argmax(0)[0]
        if (maxAnalog == 1):
            return (0.0, 0.0)
        elif (maxAnalog == 2):
            return (0.0, .5)
        elif (maxAnalog == 3):
            return (0.0, 1.0)
        elif (maxAnalog == 4):
            return (.5, 0.0)
        elif (maxAnalog == 5):
            return (.5, 1.0)
        elif (maxAnalog == 6):
            return (1.0, 0.0)
        elif (maxAnalog == 7):
            return (1.0, .5)
        elif (maxAnalog == 8):
            return (1.0, 1.0)
        elif (maxAnalog == 9):
            return (0.0, .25)
        elif (maxAnalog == 10):
            return (0.0, .75)
        elif (maxAnalog == 11):
            return (.25, 0.0)
        elif (maxAnalog == 12):
            return (.25, .25)
        elif (maxAnalog == 13):
            return (.25, .75)
        elif (maxAnalog == 14):
            return (.75, 0.0)
        elif (maxAnalog == 15):
            return (.75, .25)
        elif (maxAnalog == 16):
            return (.75, .75)
        return (.5, .5)

    def process(self, network : ComputableNetwork, controller : melee.Controller, state : ndarray):
        network.input(state)
        network.compute()
        outputs = network.output()
        print(outputs)
        button1 = outputs[1][0, ...].argmax(0)[0]
        button2 = outputs[2][0, ...].argmax(0)[0]
        pressA = button1 == 1 or button2 == 1
        pressB = button1 == 2 or button2 == 2
        pressZ = button1 == 3 or button2 == 3
        pressY = button1 == 4 or button2 == 4
        main_stick_x, main_stick_y = self.processAnalog(outputs[0][0, ...])
        c_stick_x, c_stick_y = self.processAnalog(outputs[0][1, ...])
        leftShoulder = 0
        if button1 == 5 or button2 ==5:
            leftShoulder = .1
        if button1 == 6 or button2 == 6:
            leftShoulder = 1.0
        self.processMessage({
            "a": pressA,
            "b": pressB,
            "y": pressY,
            "z": pressZ,
            "mainStickX": main_stick_x,
            "mainStickY": main_stick_y,
            "cStickX": c_stick_x,
            "cStickY": c_stick_y,
            "leftShoulder": leftShoulder,
        }, controller)