package com.example.gameMatrix.service;

import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.*;

@Service
public class MatrixService {

    public Map<String, Object> solveGame(List<List<Double>> payoffMatrix) {
        Map<String, Object> solution = new HashMap<>();
        
        // 1. Проверка на седловую точку
        solution.put("step1", "1. Поиск седловой точки");
        Map<String, Object> saddlePointResult = findSaddlePoint(payoffMatrix);
        solution.put("saddlePointResult", saddlePointResult);
        
        if ((boolean) saddlePointResult.get("exists")) {
            return solution;
        }
        
        // 2. Проверка на доминирование
        solution.put("step2", "2. Упрощение матрицы");
        Map<String, Object> dominanceResult = eliminateDominatedStrategies(payoffMatrix);
        solution.put("dominanceResult", dominanceResult);
        List<List<Double>> reducedMatrix = (List<List<Double>>) dominanceResult.get("reducedMatrix");
        
        // 3. Решение в смешанных стратегиях
        solution.put("step3", "3. Решение через линейное программирование");
        Map<String, Object> mixedStrategyResult = solveWithLinearProgramming(reducedMatrix);
        solution.put("mixedStrategyResult", mixedStrategyResult);
        
        return solution;
    }

    private Map<String, Object> findSaddlePoint(List<List<Double>> matrix) {
        Map<String, Object> result = new HashMap<>();
        
        int rows = matrix.size();
        int cols = matrix.get(0).size();
        
        // Находим минимальные значения в строках
        double[] rowMins = new double[rows];
        for (int i = 0; i < rows; i++) {
            rowMins[i] = matrix.get(i).stream().min(Double::compare).get();
        }
        
        // Находим максимальные значения в столбцах
        double[] colMaxs = new double[cols];
        for (int j = 0; j < cols; j++) {
            final int col = j;
            colMaxs[j] = matrix.stream().mapToDouble(row -> row.get(col)).max().getAsDouble();
        }
        
        // Ищем седловую точку
        double maxOfMins = Arrays.stream(rowMins).max().getAsDouble();
        double minOfMaxs = Arrays.stream(colMaxs).min().getAsDouble();
        
        if (maxOfMins == minOfMaxs) {
            result.put("exists", true);
            result.put("value", maxOfMins);
            result.put("explanation", String.format(
                "Нижняя цена игры (α): %.2f, Верхняя цена игры (β): %.2f. α = β => решение в чистых стратегиях.",
                maxOfMins, minOfMaxs));
        } else {
            result.put("exists", false);
            result.put("explanation", String.format(
                "Нижняя цена игры (α): %.2f, Верхняя цена игры (β): %.2f. α ≠ β => решение в смешанных стратегиях.",
                maxOfMins, minOfMaxs));
        }
        
        return result;
    }

    private Map<String, Object> eliminateDominatedStrategies(List<List<Double>> matrix) {
        Map<String, Object> result = new HashMap<>();
        List<List<Double>> reducedMatrix = new ArrayList<>(matrix);
        boolean changed;
        
        do {
            changed = false;
            
            // Проверка доминирования строк
            for (int i = 0; i < reducedMatrix.size(); i++) {
                for (int j = 0; j < reducedMatrix.size(); j++) {
                    if (i != j && isRowDominated(reducedMatrix, i, j)) {
                        reducedMatrix.remove(i);
                        changed = true;
                        result.put("rowDomination", String.format(
                            "Строка %d доминируется строкой %d и была удалена", i+1, j+1));
                        break;
                    }
                }
                if (changed) break;
            }
            
            if (!changed) {
                // Проверка доминирования столбцов
                for (int i = 0; i < reducedMatrix.get(0).size(); i++) {
                    for (int j = 0; j < reducedMatrix.get(0).size(); j++) {
                        if (i != j && isColDominated(reducedMatrix, i, j)) {
                            for (List<Double> row : reducedMatrix) {
                                row.remove(i);
                            }
                            changed = true;
                            result.put("colDomination", String.format(
                                "Столбец %d доминируется столбцом %d и был удален", i+1, j+1));
                            break;
                        }
                    }
                    if (changed) break;
                }
            }
        } while (changed && !reducedMatrix.isEmpty() && !reducedMatrix.get(0).isEmpty());
        
        result.put("reducedMatrix", reducedMatrix);
        return result;
    }

    private boolean isRowDominated(List<List<Double>> matrix, int dominated, int dominant) {
        for (int j = 0; j < matrix.get(0).size(); j++) {
            if (matrix.get(dominated).get(j) > matrix.get(dominant).get(j)) {
                return false;
            }
        }
        return true;
    }

    private boolean isColDominated(List<List<Double>> matrix, int dominated, int dominant) {
        for (int i = 0; i < matrix.size(); i++) {
            if (matrix.get(i).get(dominated) < matrix.get(i).get(dominant)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> solveWithLinearProgramming(List<List<Double>> matrix) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. Нормализация матрицы
            double minValue = getMinMatrixValue(matrix);
            double adjustment = minValue < 0 ? -minValue : 0;
            List<List<Double>> normalizedMatrix = normalizeMatrix(matrix, adjustment);
            
            result.put("adjustment", adjustment);
            result.put("normalizedMatrix", normalizedMatrix);
            
            // 2. Решение для игрока 1 (минимизация)
            double[] player1Solution = solvePlayer1Problem(normalizedMatrix);
            double sumP = Arrays.stream(player1Solution).sum();
            if (sumP <= 0) {
                throw new IllegalStateException("Сумма стратегий игрока 1 равна нулю");
            }
            double gameValue = 1.0 / sumP - adjustment;
            double[] player1Strategy = Arrays.stream(player1Solution)
                .map(p -> p / sumP)
                .toArray();
            
            // 3. Решение для игрока 2 (максимизация)
            double[] player2Solution = solvePlayer2Problem(normalizedMatrix);
            double sumQ = Arrays.stream(player2Solution).sum();
            if (sumQ <= 0) {
                throw new IllegalStateException("Сумма стратегий игрока 2 равна нулю");
            }
            double[] player2Strategy = Arrays.stream(player2Solution)
                .map(q -> q / sumQ)
                .toArray();
            
            // 4. Проверка согласованности
            double normalizedGameValueP = 1.0 / sumP;
            double normalizedGameValueQ = 1.0 / sumQ;
            if (Math.abs(normalizedGameValueP - normalizedGameValueQ) > 1e-6) {
                throw new IllegalStateException(String.format(
                    "Несогласованные решения: v1=%.6f, v2=%.6f", 
                    normalizedGameValueP, normalizedGameValueQ));
            }
            
            // 5. Сохранение результатов
            result.put("player1Strategy", Arrays.stream(player1Strategy)
                .boxed()
                .collect(Collectors.toList()));
            result.put("player2Strategy", Arrays.stream(player2Strategy)
                .boxed()
                .collect(Collectors.toList()));
            result.put("gameValue", gameValue);
            result.put("normalizedGameValue", normalizedGameValueP);
            result.put("explanation", buildExplanation(matrix, normalizedMatrix, 
                adjustment, gameValue, player1Strategy, player2Strategy));
                
        } catch (Exception e) {
            result.put("error", "Ошибка при решении: " + e.getMessage());
        }
        
        return result;
    }

    private double[] solvePlayer1Problem(List<List<Double>> matrix) {
        int m = matrix.size();
        int n = matrix.get(0).size();
        
        // Целевая функция: min Σt_i
        LinearObjectiveFunction f = new LinearObjectiveFunction(
            DoubleStream.generate(() -> 1.0).limit(m).toArray(), 0);
        
        // Ограничения: A^T * t ≥ 1
        List<LinearConstraint> constraints = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            double[] coeffs = new double[m];
            for (int i = 0; i < m; i++) {
                coeffs[i] = matrix.get(i).get(j);
            }
            constraints.add(new LinearConstraint(coeffs, Relationship.GEQ, 1));
        }
        
        PointValuePair solution = new SimplexSolver().optimize(
            f, 
            new LinearConstraintSet(constraints), 
            GoalType.MINIMIZE, 
            new NonNegativeConstraint(true));
        
        return solution.getPoint();
    }

    private double[] solvePlayer2Problem(List<List<Double>> matrix) {
        int m = matrix.size();
        int n = matrix.get(0).size();
        
        // Целевая функция: max Σu_j
        LinearObjectiveFunction f = new LinearObjectiveFunction(
            DoubleStream.generate(() -> 1.0).limit(n).toArray(), 0);
        
        // Ограничения: A * u ≤ 1
        List<LinearConstraint> constraints = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            double[] coeffs = matrix.get(i).stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
            constraints.add(new LinearConstraint(coeffs, Relationship.LEQ, 1));
        }
        
        PointValuePair solution = new SimplexSolver().optimize(
            f, 
            new LinearConstraintSet(constraints), 
            GoalType.MAXIMIZE, 
            new NonNegativeConstraint(true));
        
        return solution.getPoint();
    }

    private double getMinMatrixValue(List<List<Double>> matrix) {
        return matrix.stream()
            .flatMap(List::stream)
            .min(Double::compare)
            .orElse(0.0);
    }

    private List<List<Double>> normalizeMatrix(List<List<Double>> matrix, double adjustment) {
        return matrix.stream()
            .map(row -> row.stream()
                .map(val -> val + adjustment)
                .collect(Collectors.toList()))
            .collect(Collectors.toList());
    }

    private String buildExplanation(List<List<Double>> originalMatrix,
                                  List<List<Double>> normalizedMatrix,
                                  double adjustment,
                                  double gameValue,
                                  double[] p1Strategy,
                                  double[] p2Strategy) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("### Исходная матрица ###\n")
          .append(matrixToString(originalMatrix)).append("\n\n");
        
        if (adjustment > 0) {
            sb.append("### Нормализованная матрица (прибавлено ")
              .append(String.format("%.2f", adjustment))
              .append(") ###\n")
              .append(matrixToString(normalizedMatrix)).append("\n\n");
        }
        
        sb.append("### Результаты ###\n")
          .append("Цена игры: ").append(String.format("%.6f", gameValue)).append("\n\n")
          .append("Стратегия Игрока 1:\n");
        
        for (int i = 0; i < p1Strategy.length; i++) {
            sb.append(String.format("p%d = %.6f\n", i+1, p1Strategy[i]));
        }
        
        sb.append("\nСтратегия Игрока 2:\n");
        for (int j = 0; j < p2Strategy.length; j++) {
            sb.append(String.format("q%d = %.6f\n", j+1, p2Strategy[j]));
        }
        
        return sb.toString();
    }

    private String matrixToString(List<List<Double>> matrix) {
        return matrix.stream()
            .map(row -> row.stream()
                .map(val -> String.format("%8.2f", val))
                .collect(Collectors.joining(" ")))
            .collect(Collectors.joining("\n"));
    }

    private List<List<Double>> transposeMatrix(List<List<Double>> matrix) {
        int m = matrix.size();
        int n = matrix.get(0).size();
        
        List<List<Double>> transposed = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            List<Double> row = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                row.add(matrix.get(i).get(j));
            }
            transposed.add(row);
        }
        
        return transposed;
    }
}