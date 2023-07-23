import numpy as np
from collections import deque

class LimitedSizeList:
    def __init__(self, size_limit):
        self.size_limit = size_limit
        self.data = deque(maxlen=size_limit)

    def add(self, new_items):
        if isinstance(new_items, list):
            for item in reversed(new_items):
                if isinstance(item, np.ndarray):
                    self.data.appendleft(item)
        elif isinstance(new_items, np.ndarray):
            self.data.appendleft(new_items)

    def get_data(self):
        return list(self.data)
