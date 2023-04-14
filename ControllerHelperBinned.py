import math
from typing import Dict

import melee
from numpy import ndarray

from melee.enums import Button

from ComputableNetwork import ComputableNetwork

def emaWeight(numSamples):
    return 2 / float(numSamples + 1)

def ema(close, prevEma, numSamples):
    return ((close-prevEma) * emaWeight(numSamples) ) + prevEma


class ControllerHelper:
    main_x = .5
    main_y = .5
    c_x = .5
    c_y = .5
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

    def process(self, network : ComputableNetwork, controller : melee.Controller, state : 'list[ndarray]', controller_state : melee.ControllerState):
        network.inputs(state)
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
        # useCStick = button1 == 7 or button2 == 7
        main_stick_x, main_stick_y = self.processAnalog(outputs[0])
        c_stick_x, c_stick_y = (.5, .5)
        # if useCStick:
        c_stick_x, c_stick_y = self.processAnalog(outputs[1])
        leftShoulder = 0
        if button1 == 5 or button2 ==5:
            leftShoulder = .31
        if button1 == 6 or button2 == 6:
            leftShoulder = 1.0
        n = 3
        # print("controllerX: " + str(controller_state.main_stick[0]) + " controllerY: " + str(controller_state.main_stick[1]))
        # print("newX: " + str(main_stick_x) + " newY: " + str(main_stick_y))
        # print("=========")
        new_main_x = ema(main_stick_x, self.main_x, n)
        new_main_y = ema(main_stick_y, self.main_y, n)
        new_c_x = ema(c_stick_x, self.c_x, n)
        new_c_y = ema(c_stick_y, self.c_y, n)
        self.processMessage({
            "a": pressA,
            "b": pressB,
            "y": pressY,
            "z": pressZ,
            "mainStickX": new_main_x,
            "mainStickY": new_main_y,
            "cStickX": new_c_x,
            "cStickY": new_c_y,
            "leftShoulder": leftShoulder,
        }, controller)
        self.main_x = new_main_x
        self.main_y = new_main_y
        self.c_x = new_c_x
        self.c_y = new_c_y
