import tkinter as tk
from tkinter import messagebox
from constants import BOARD_SIZE, CELL_SIZE
from game import Game

class GameUI:
    def __init__(self, root, game):

        self.root = root
        self.game = game


        self.canvas = tk.Canvas(root, width=BOARD_SIZE*CELL_SIZE, height=BOARD_SIZE*CELL_SIZE)
        self.canvas.pack()

        self.status_label = tk.Label(root, text="Welcome!")
        self.status_label.pack()

        self.move_counter_label = tk.Label(root, text=f"Moves: {self.game.total_moves}/{self.game.MAX_MOVES}")
        self.move_counter_label.pack()

        self.canvas.bind("<Button-1>", self.on_click)  #tıklama olayı

        self.selected_pos = None
        self.human_moves_made = 0
        self.human_first_move_piece = None

        self.draw_board()   #tahta çizer

        self.root.after(1000, self.ai_turn)

    def draw_board(self):

        self.canvas.delete("all")

        highlight_moves = []

        if self.selected_pos is not None:
            sr, sc = self.selected_pos
            highlight_moves = self.game.valid_moves_for_piece(sr, sc)

        for r in range(BOARD_SIZE):

            for c in range(BOARD_SIZE):   
                x1 = c * CELL_SIZE
                y1 = r * CELL_SIZE
                x2 = x1 + CELL_SIZE
                y2 = y1 + CELL_SIZE

                

                fill_color = "white"

                if self.selected_pos == (r, c):
                    fill_color = "#ffeeba"  # sarı tonu

                elif (r, c) in highlight_moves:
                    fill_color = "#d3d3d3"  # gri tonu

                self.canvas.create_rectangle(x1, y1, x2, y2, fill=fill_color, outline="black")

                piece = self.game.board[r][c]

                if piece == 'T':
                    self.canvas.create_polygon(
                        x1 + CELL_SIZE / 2, y1 + 10,
                        x1 + 10, y2 - 10,
                        x2 - 10, y2 - 10,
                        fill="blue"
                    )

                elif piece == 'O':

                    self.canvas.create_oval(x1 + 10, y1 + 10, x2 - 10, y2 - 10, fill="red")

    def ai_turn(self):

        if self.game.current_player == 1:
            result = self.game.ai_move()
            
            self.selected_pos = None   # hamle sonrası seçimi temizler
            self.update_move_counter()
            self.draw_board()

            if result:
                self.status_label.config(text="Game Over: " + result)
                self.ask_restart(result)

            else:
                self.status_label.config(text="It's your turn.")

    def ask_restart(self, result):

        answer = messagebox.askyesno("Game Over", f"{result}\nDo you want to play again?")
        
        if answer:
            self.reset_game()

        else:
            self.root.quit()

    def reset_game(self):

        self.game = Game()
        self.selected_pos = None
        self.human_moves_made = 0
        self.human_first_move_piece = None

        self.update_move_counter()
        self.draw_board()

        self.status_label.config(text="Welcome!")
       
        self.root.after(1000, self.ai_turn)

    def on_click(self, event):

        if self.game.current_player != 2:
            return

        c = event.x // CELL_SIZE
        r = event.y // CELL_SIZE

        if r < 0 or r >= BOARD_SIZE or c < 0 or c >= BOARD_SIZE:
            return

        piece = self.game.board[r][c]

        if self.selected_pos is None:
            if piece == 'O':
                if self.human_moves_made == 1 and self.human_first_move_piece is not None:
                    if (r, c) == self.human_first_move_piece:
                        self.status_label.config(text="Choose another piece for second move!")
                        return
                    
                self.selected_pos = (r, c)
                self.status_label.config(text="Pick where to go.")
                self.draw_board()

            else:
                self.status_label.config(text="Please choose your pieces (red circle).")
        
        else:
            start_pos = self.selected_pos
            
            if start_pos == (r, c):
                self.selected_pos = None
                self.status_label.config(text="Move cancelled. Choose a piece.")
                self.draw_board()
                return

            valid_moves = self.game.valid_moves_for_piece(start_pos[0], start_pos[1])
            
            if (r, c) in valid_moves:
                
                if self.human_moves_made == 0:
                    result = self.game.human_move(start_pos, (r, c))
                   
                    self.selected_pos = None    # hamle sonrası seçimi temizler
                    self.update_move_counter()
                    self.draw_board()
                    
                    if result == "need_second_move":
                        self.human_first_move_piece = (r, c)
                        self.human_moves_made = 1
                        self.status_label.config(text="You must make the second move with another piece.")
                    
                    elif result:
                        self.status_label.config(text="Game Over: " + result)
                        self.ask_restart(result)
                    
                    else:
                        self.human_moves_made = 0
                        self.human_first_move_piece = None
                        self.status_label.config(text="AI's turn...")
                        self.root.after(1000, self.ai_turn)
                
                else:
                    result = self.game.human_second_move(start_pos, (r, c))
                   
                    self.selected_pos = None   # hamle sonrası seçimi temizler
                    self.update_move_counter()
                    self.draw_board()
                    self.human_moves_made = 0
                    self.human_first_move_piece = None

                    if result:
                        self.status_label.config(text="Game Over: " + result)
                        self.ask_restart(result)
                    else:
                        self.status_label.config(text="AI's turn...")
                        self.root.after(1000, self.ai_turn)
            else:
                self.status_label.config(text="Invalid move, please try again.")
                self.selected_pos = None
                self.draw_board()

    def update_move_counter(self):
        
        self.move_counter_label.config(text=f"Moves: {self.game.total_moves}/{self.game.MAX_MOVES}")
