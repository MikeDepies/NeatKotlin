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
        maxAnalog = analogOutput.argmax(0)
        shape = analogOutput.shape
        return (maxAnalog[0] / (shape[0] - 1), maxAnalog[1] / (shape [1] - 1))

    def process(self, network : ComputableNetwork, controller : melee.Controller, state : ndarray):
        network.input(state)
        network.compute()
        outputs = network.output()
        # print(outputs[0].shape)
        # print(outputs[1].shape)
        # print(outputs[2].shape)
        # print(outputs[1][0, ...])
        # print(outputs[1][0, ...].argmax(0))
        button1 = outputs[2][0, ...].argmax(0)
        button2 = outputs[3][0, ...].argmax(0)
        pressA = button1 == 1 or button2 == 1
        pressB = button1 == 2 or button2 == 2
        pressZ = button1 == 3 or button2 == 3
        pressY = button1 == 4 or button2 == 4
        main_stick_x, main_stick_y = self.processAnalog(outputs[0])
        c_stick_x, c_stick_y = self.processAnalog(outputs[1])
        leftShoulder = 0
        if button1 == 5 or button2 ==5:
            leftShoulder = .31
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