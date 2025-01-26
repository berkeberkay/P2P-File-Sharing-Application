from constants import BOARD_SIZE, CELL_SIZE, MAX_MOVES
import copy
import random
from minimax import alpha_beta_minimax

class Game:
    def __init__(self):
        self.board = self.init_board()
        self.current_player = 1  
        self.total_moves = 0
        self.MAX_MOVES = MAX_MOVES  

    def init_board(self): #başlangıç diziliş
        
        board = []
        for i in range(BOARD_SIZE):
            row = []
            for j in range(BOARD_SIZE):
                row.append('.') 
            board.append(row)

        #ai taşları
        board[0][0] = 'T'  
        board[2][0] = 'T'  
        board[4][6] = 'T' 
        board[6][6] = 'T'  

        #human taşları
        board[0][6] = 'O'  
        board[2][6] = 'O'  
        board[4][0] = 'O'  
        board[6][0] = 'O'  

        
        return board


    def switch_player(self):

        if self.current_player == 1:

            self.current_player = 2

        else:
            self.current_player = 1
            

    def is_game_over(self):
        
        t_count = self.get_pieces_count(1)  # oyuncuların taşlarının sayımı
        o_count = self.get_pieces_count(2)

        if t_count == 0 and o_count == 0:
            return "Draw"
        
        if t_count == 1 and o_count == 1:
            return "Draw"
        
        if t_count == 0 and o_count > 0:
            return "Human wins"
        
        if o_count == 0 and t_count > 0:
            return "AI wins"
        
        if self.total_moves >= self.MAX_MOVES:  #50 hamle olduysa taş sayılarına göre

            if t_count == o_count:
                return "Draw"
            
            elif t_count > o_count:
                return "AI wins"
            
            else:
                return "Human wins"
            
        return None

    def get_pieces_count(self, player):

        if player == 1:
            p_char = 'T'  
        else:
            p_char = 'O'  

        count = 0 

        for r in range(BOARD_SIZE):

            for c in range(BOARD_SIZE):

                if self.board[r][c] == p_char:
                    count += 1

        return count

    def get_pieces_count_from_board(self, board, player):

        if player == 1:
            p_char = 'T'  
        else:
            p_char = 'O'  

        count = 0  

        for r in range(BOARD_SIZE):

            for c in range(BOARD_SIZE):

                if board[r][c] == p_char:
                    count += 1

        return count

    def get_all_pieces_positions(self, player, board=None):

        if board is None:
            board = self.board

        if player == 1:
            p_char = 'T' 
        else:
            p_char = 'O'  

        positions = []  

        for r in range(BOARD_SIZE):

            for c in range(BOARD_SIZE):

                if board[r][c] == p_char:
                    positions.append((r, c))

        return positions

    def valid_moves_for_piece(self, r, c, board=None):

        if board is None:
            board = self.board

        if board[r][c] == '.':
            return []
        
        moves = []

        directions = [(-1, 0), (1, 0), (0, -1), (0, 1)]

        for dr, dc in directions:
            nr, nc = r + dr, c + dc

            if 0 <= nr < BOARD_SIZE and 0 <= nc < BOARD_SIZE and board[nr][nc] == '.':
                moves.append((nr, nc))
        return moves

    def get_all_possible_moves(self, player, board=None):

        if board is None:
            board = self.board
        positions = self.get_all_pieces_positions(player, board)
        all_moves = []

        for (r, c) in positions:
            v_moves = self.valid_moves_for_piece(r, c, board)

            for mv in v_moves:
                all_moves.append(((r, c), mv))

        return all_moves

    def make_move(self, start_pos, end_pos, board=None, total_moves_increment=True):

        if board is None:
            board = self.board

        sr, sc = start_pos  # sr start row
        er, ec = end_pos  # er end row

        piece = board[sr][sc]

        board[sr][sc] = '.'
        board[er][ec] = piece

        if total_moves_increment:
            self.total_moves += 1
        self.check_captures(er, ec, board)

    def check_captures(self, r, c, board=None):

        if board is None:
            board = self.board

        
        row_line = []     # rowdaki pozisyonları saklamak için liste 
        for col in range(BOARD_SIZE): 
            row_line.append((r, col))  

        
        col_line = []    # column pozisyonları saklamak için liste
        for row in range(BOARD_SIZE):
            col_line.append((row, c))


        captured_in_row = self.check_line_capture(row_line, board)
        captured_in_col = self.check_line_capture(col_line, board)

        all_captured = captured_in_row + captured_in_col

        for (rr, cc) in all_captured:
            board[rr][cc] = '.'

    def check_line_capture(self, line, board):
               
        symbols = []    # sembolleri saklamak için r liste
        for r, c in line: 
            symbols.append(board[r][c])   # sembolü listeye ekle
        
        captured_positions = []    # captured pozisyonları saklamak için liste


        def check_segment(start, end):

            base_char = symbols[start]

            if base_char == '.':
                return
            
            for i in range(start + 1, end + 1):
                if symbols[i] != base_char:
                    return

            if base_char == 'T':
                enemy_char = 'O'
            else:
                enemy_char = 'T'

            left_boundary = symbols[start - 1] if start > 0 else None
            right_boundary = symbols[end + 1] if end < len(symbols) - 1 else None

            left_is_enemy = (left_boundary == enemy_char)
            right_is_enemy = (right_boundary == enemy_char)
            left_is_wall = (start == 0)
            right_is_wall = (end == len(symbols) - 1)

            if (left_is_wall and right_is_enemy) or (right_is_wall and left_is_enemy) or (left_is_enemy and right_is_enemy):
                for i in range(start, end + 1):
                    captured_positions.append(line[i])

        i = 0
        while i < len(symbols):
            if symbols[i] == '.':
                i += 1
                continue
            j = i
            while j + 1 < len(symbols) and symbols[j + 1] == symbols[i]:
                j += 1
            check_segment(i, j)
            i = j + 1

        return captured_positions

    def simulate_move(self, board, start_pos, end_pos):

        new_board = copy.deepcopy(board)

        sr, sc = start_pos 
        er, ec = end_pos 

        piece = new_board[sr][sc]
        new_board[sr][sc] = '.'
        new_board[er][ec] = piece

        self.check_captures(er, ec, new_board)

        return new_board

    def evaluate_board(self, board):
      
        ai_pieces = self.get_pieces_count_from_board(board, 1)
        human_pieces = self.get_pieces_count_from_board(board, 2)

        ai_mobility = len(self.get_all_possible_moves(1, board))
        human_mobility = len(self.get_all_possible_moves(2, board))

        ai_center = self.center_control(1, board)
        human_center = self.center_control(2, board)

        score = (ai_pieces - human_pieces) * 50 + (ai_mobility - human_mobility) * 10 + (ai_center - human_center) * 20

        return score

    def center_control(self, player, board):
    
        central_positions = [
            (3, 3), (3, 2), (3, 4),
            (2, 3), (2, 2), (2, 4),
            (4, 3), (4, 2), (4, 4)
        ]

        if player == 1:
            p_char = 'T'  
        else:
            p_char = 'O'  

        count = 0 

        for position in central_positions:  
            r, c = position  

            if board[r][c] == p_char:  #
                count += 1 

        return count


    def check_game_over_in_board(self, board):

        t_count = self.get_pieces_count_from_board(board, 1)
        o_count = self.get_pieces_count_from_board(board, 2)

        if t_count == 0 and o_count == 0:
            return "Draw"
        if t_count == 1 and o_count == 1:
            return "Draw"
        if t_count == 0 and o_count > 0:
            return "Human wins"
        if o_count == 0 and t_count > 0:
            return "AI wins"
        return None

    def ai_move(self):

        player = 1
        moves = self.get_all_possible_moves(player, self.board)

        if not moves:
            self.switch_player()
            return self.is_game_over()

        random.shuffle(moves)

        num_pieces = self.get_pieces_count(1)
        moves_to_do = 1 if num_pieces == 1 else 2

        best_value = -9999
        best_first_moves = []
        best_second_move = None

        depth = 2      #ağaç arama derinliği
        alpha = -9999
        beta = 9999

        if moves_to_do == 1:

            for m in moves:
                new_board = self.simulate_move(self.board, m[0], m[1])
                value = alpha_beta_minimax(self, new_board, depth - 1, alpha, beta, False)

                if value > best_value:
                    best_value = value
                    best_first_moves = [m]

                elif value == best_value:
                    best_first_moves.append(m)
                alpha = max(alpha, best_value)
                
                if beta <= alpha:
                    break

            if best_first_moves:
                chosen_move = random.choice(best_first_moves)
                self.make_move(chosen_move[0], chosen_move[1])
            else:
                self.make_move(moves[0][0], moves[0][1])
        else:
            for m in moves:
                first_move_board = self.simulate_move(self.board, m[0], m[1])
                second_moves = self.get_all_possible_moves(player, first_move_board)
                random.shuffle(second_moves)
                first_move_end_pos = m[1]

                filtered_second_moves = [sm for sm in second_moves if sm[0] != first_move_end_pos]

                if not filtered_second_moves:
                    value = alpha_beta_minimax(self, first_move_board, depth - 1, alpha, beta, False)

                    if value > best_value:
                        best_value = value
                        best_first_moves = [(m, None)]

                    elif value == best_value:
                        best_first_moves.append((m, None))
                    alpha = max(alpha, best_value)

                    if beta <= alpha:
                        break

                else:
                    local_best = -9999
                    local_best_moves = []

                    for sm in filtered_second_moves:
                        second_move_board = self.simulate_move(first_move_board, sm[0], sm[1])
                        value = alpha_beta_minimax(self, second_move_board, depth - 1, alpha, beta, False)

                        if value > local_best:
                            local_best = value
                            local_best_moves = [sm]

                        elif value == local_best:
                            local_best_moves.append(sm)

                    if local_best > best_value:
                        best_value = local_best
                        best_first_moves = [(m, random.choice(local_best_moves))]

                    elif local_best == best_value:
                        best_first_moves.append((m, random.choice(local_best_moves)))
                    alpha = max(alpha, best_value)

                    if beta <= alpha:
                        break

            if best_first_moves:
                chosen_combo = random.choice(best_first_moves)
                self.make_move(chosen_combo[0][0], chosen_combo[0][1])

                if chosen_combo[1] is not None:
                    self.make_move(chosen_combo[1][0], chosen_combo[1][1])

            else:
                self.make_move(moves[0][0], moves[0][1])

        result = self.is_game_over()

        if result:
            return result
        
        self.switch_player()
        return None

    def human_move(self, start_pos, end_pos):

        sr, sc = start_pos  # sr startrow

        if self.board[sr][sc] != 'O':
            return None
        
        self.make_move(start_pos, end_pos)
        result = self.is_game_over()

        if result:
            return result
        
        num_pieces = self.get_pieces_count(2)

        if num_pieces > 1:
            return "need_second_move"
        else:
            self.switch_player()
            return None

    def human_second_move(self, start_pos, end_pos):
        
        sr, sc = start_pos  # sr start row

        if self.board[sr][sc] != 'O':
            return None
        
        self.make_move(start_pos, end_pos)
        result = self.is_game_over()

        if result:
            return result
        
        self.switch_player()

        return None
