import copy
from functools import lru_cache

def board_to_tuple(board):
    return tuple(tuple(row) for row in board)

def alpha_beta_minimax(game, board, depth, alpha, beta, maximizing_player):

    board_tuple = board_to_tuple(board)
    result = game.check_game_over_in_board(board)

    if depth == 0 or result is not None:

        if result == "AI wins":
            return 999
        
        elif result == "Human wins":
            return -999
        
        elif result == "Draw":
            return 0
        
        else:
            return game.evaluate_board(board)
               
    if maximizing_player:
        player = 1  

    else:
        player = 2  

    moves = game.get_all_possible_moves(player, board)
  
    num_pieces = game.get_pieces_count_from_board(board, player)

    if num_pieces == 1:  
        moves_to_do = 1  
    else: 
        moves_to_do = 2  

    if not moves:
        
        return game.evaluate_board(board) # move available değilse eva. döndür

    if maximizing_player:
        
        best_value = -9999   # ai max etmeye çalışır

        sorted_moves = sorted(moves, key=lambda m: game.evaluate_board(game.simulate_move(board, m[0], m[1])), reverse=True)
        
        if moves_to_do == 1:
            
            for m in sorted_moves:
                new_board = game.simulate_move(board, m[0], m[1])
                value = alpha_beta_minimax(game, new_board, depth-1, alpha, beta, False)
                
                if value > best_value:
                    best_value = value
                alpha = max(alpha, best_value)
                
                if beta <= alpha:
                    break
            
            return best_value
        
        else:

            for m in sorted_moves:
                first_move_board = game.simulate_move(board, m[0], m[1])
                second_moves = game.get_all_possible_moves(player, first_move_board)
                sorted_second_moves = sorted(second_moves, key=lambda sm: game.evaluate_board(game.simulate_move(first_move_board, sm[0], sm[1])), reverse=True)
                filtered_second_moves = [sm for sm in sorted_second_moves if sm[0] != m[1]]
                
                if not filtered_second_moves:
                    value = alpha_beta_minimax(game, first_move_board, depth-1, alpha, beta, False)
                    
                    if value > best_value:
                        best_value = value
                    alpha = max(alpha, best_value)
                    
                    if beta <= alpha:
                        break
                
                else:
                    
                    for sm in filtered_second_moves:
                        second_move_board = game.simulate_move(first_move_board, sm[0], sm[1])
                        value = alpha_beta_minimax(game, second_move_board, depth-1, alpha, beta, False)
                        
                        if value > best_value:
                            best_value = value
                        alpha = max(alpha, best_value)
                        
                        if beta <= alpha:
                            break
            
            return best_value
    
    else:
        
        best_value = 9999  # insan minimize etmeye çalışır
      
        sorted_moves = sorted(moves, key=lambda m: game.evaluate_board(game.simulate_move(board, m[0], m[1])))
        
        if moves_to_do == 1:
            
            for m in sorted_moves:
                new_board = game.simulate_move(board, m[0], m[1])
                value = alpha_beta_minimax(game, new_board, depth-1, alpha, beta, True)
                
                if value < best_value:
                    best_value = value
                beta = min(beta, best_value)
                
                if beta <= alpha:
                    break
            
            return best_value
       
        else:
            
            for m in sorted_moves:
                first_move_board = game.simulate_move(board, m[0], m[1])
                second_moves = game.get_all_possible_moves(player, first_move_board)
                sorted_second_moves = sorted(second_moves, key=lambda sm: game.evaluate_board(game.simulate_move(first_move_board, sm[0], sm[1])))
                filtered_second_moves = [sm for sm in sorted_second_moves if sm[0] != m[1]]
                
                if not filtered_second_moves:
                    value = alpha_beta_minimax(game, first_move_board, depth-1, alpha, beta, True)
                    
                    if value < best_value:
                        best_value = value
                    beta = min(beta, best_value)
                   
                    if beta <= alpha:
                        break
                
                else:
                    
                    for sm in filtered_second_moves:
                        second_move_board = game.simulate_move(first_move_board, sm[0], sm[1])
                        value = alpha_beta_minimax(game, second_move_board, depth-1, alpha, beta, True)
                        
                        if value < best_value:
                            best_value = value
                        beta = min(beta, best_value)
                        
                        if beta <= alpha:
                            break
            
            return best_value
