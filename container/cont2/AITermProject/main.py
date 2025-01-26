from tkinter import Tk
from game import Game
from ui import GameUI

def main():
    root = Tk()
    root.title("Strategic Board Game")
    game = Game()
    ui = GameUI(root, game)
    root.mainloop()

if __name__ == "__main__":
    main()
