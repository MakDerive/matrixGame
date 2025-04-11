package com.example.gameMatrix.service;

import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NonNegativeConstraint;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class MatrixService {
    
	 public Map<String, Object> solveGame(List<List<Double>> payoffMatrix) {
	        Map<String, Object> solution = new HashMap<>();
	        
	        // 1. Проверка на седловую точку
	        solution.put("step1", "1. Поиск седловой точки (чистых стратегий)");
	        Map<String, Object> saddlePointResult = findSaddlePoint(payoffMatrix);
	        solution.put("saddlePointResult", saddlePointResult);
	        
	        if ((boolean) saddlePointResult.get("exists")) {
	            return solution;
	        }
	        
	        // 2. Проверка на доминирование
	        solution.put("step2", "2. Упрощение матрицы путем исключения доминируемых стратегий");
	        Map<String, Object> dominanceResult = eliminateDominatedStrategies(payoffMatrix);
	        solution.put("dominanceResult", dominanceResult);
	        List<List<Double>> reducedMatrix = (List<List<Double>>) dominanceResult.get("reducedMatrix");
	        // 3. Решение в смешанных стратегиях
	        solution.put("step3", "3. Решение в смешанных стратегиях");
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
        for (int j =  0; j < cols; j++) {
            final int col = j;
            colMaxs[j] = matrix.stream().mapToDouble(row -> row.get(col)).max().getAsDouble();
        }
        
        // Ищем седловую точку
        double maxOfMins = Arrays.stream(rowMins).max().getAsDouble();
        double minOfMaxs = Arrays.stream(colMaxs).min().getAsDouble();
        
        if (maxOfMins == minOfMaxs) {
            result.put("exists", true);
            result.put("value", maxOfMins);
            result.put("explanation", "Нижняя цена игры (α): " + maxOfMins + 
                           ", Верхняя цена игры (β): " + minOfMaxs + 
                           ". α = β => игра имеет решение в чистых стратегиях.");
        } else {
            result.put("exists", false);
            result.put("explanation", "Нижняя цена игры (α): " + maxOfMins + 
                           ", Верхняя цена игры (β): " + minOfMaxs + 
                           ". α ≠ β => игра не имеет решения в чистых стратегиях.");
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
                        result.put("rowDomination", "Строка " + (i+1) + " доминируется строкой " + (j+1) + " и была удалена");
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
                            result.put("colDomination", "Столбец " + (i+1) + " доминируется столбцом " + (j+1) + " и был удален");
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
    
    private Map<String, Object> solveWithLinearProgramming(List<List<Double>> payoffMatrix) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. Нормализация матрицы (делаем все элементы неотрицательными)
            double minValue = payoffMatrix.stream()
                .flatMap(List::stream)
                .min(Double::compare)
                .orElse(0.0);
            
            double adjustment = minValue < 0 ? -minValue : 0;
            List<List<Double>> normalizedMatrix = payoffMatrix.stream()
                .map(row -> row.stream()
                    .map(val -> val + adjustment)
                    .collect(Collectors.toList()))
                .collect(Collectors.toList());
            result.put("adjustment", adjustment);
            result.put("normalizedMatrix", normalizedMatrix);
            // 2. Решение нормализованной задачи для игрока 1
            double[] pSolution = solvePlayerProblem(normalizedMatrix, true);
            System.out.println(Arrays.toString(pSolution));
            double normalizedGameValue = 1.0 / Arrays.stream(pSolution).sum();
            double[] player1Strategy = Arrays.stream(pSolution)
                .map(x -> x * normalizedGameValue)
                .toArray();

            // 3. Решение нормализованной задачи для игрока 2
            double[] qSolution = solvePlayerProblem(transposeMatrix(normalizedMatrix), false);
            double[] player2Strategy = Arrays.stream(qSolution)
                .map(x -> x * normalizedGameValue)
                .toArray();

            // 4. Корректировка цены игры
            double gameValue = normalizedGameValue - adjustment;

            // 5. Формирование результатов
            result.put("player1Strategy", Arrays.stream(player1Strategy).boxed().collect(Collectors.toList()));
            result.put("player2Strategy", Arrays.stream(player2Strategy).boxed().collect(Collectors.toList()));
            result.put("gameValue", gameValue);
            result.put("normalizedGameValue", normalizedGameValue);

            // 6. Формирование пояснения
            StringBuilder explanation = new StringBuilder();
            explanation.append("Решение найдено методом линейного программирования:\n");
            
            if (adjustment > 0) {
                explanation.append("Матрица была нормализована (прибавлено ")
                         .append(adjustment)
                         .append(" ко всем элементам)\n");
                explanation.append("Цена нормализованной игры: ")
                         .append(normalizedGameValue)
                         .append("\n");
            }
            
            explanation.append("Оптимальная стратегия игрока 1 (P): ")
                     .append(Arrays.toString(player1Strategy))
                     .append("\n");
            explanation.append("Оптимальная стратегия игрока 2 (Q): ")
                     .append(Arrays.toString(player2Strategy))
                     .append("\n");
            explanation.append("Итоговая цена игры: ")
                     .append(gameValue);
            
            result.put("explanation", explanation.toString());
            
        } catch (Exception e) {
            result.put("error", "Ошибка при решении задачи линейного программирования: " + e.getMessage());
        }
        
        return result;
    }
    
    private double[] solvePlayerProblem(List<List<Double>> matrix, boolean isMaximizingPlayer) {
        int m = matrix.size();
        int n = matrix.get(0).size();
        // Целевая функция: минимизация или максимизация
        LinearObjectiveFunction f = new LinearObjectiveFunction(
            isMaximizingPlayer ? 
                IntStream.range(0, m).mapToDouble(i -> 1.0).toArray() :
                IntStream.range(0, n).mapToDouble(i -> 1.0).toArray(),
            0);
        // Ограничения
        List<LinearConstraint> constraints = new ArrayList<>();
        if (isMaximizingPlayer) {
            // Для игрока 1: p1*a11 + p2*a21 + ... + pm*am1 >= v и т.д.
            for (int j = 0; j < n; j++) {
                double[] coefficients = new double[m];
                for (int i = 0; i < m; i++) {
                    coefficients[i] = matrix.get(i).get(j);
                }
                constraints.add(new LinearConstraint(coefficients, Relationship.GEQ, 1));
            }
        } else {
            // Для игрока 2: q1*a11 + q2*a12 + ... + qn*a1n <= v и т.д.
            for (int i = 0; i < m; i++) {
                double[] coefficients = matrix.get(i).stream().mapToDouble(Double::doubleValue).toArray();
                constraints.add(new LinearConstraint(coefficients, Relationship.LEQ, 1));
            }
        }
        // Неотрицательные переменные
        NonNegativeConstraint nonNegative = new NonNegativeConstraint(true);
        
        // Решение задачи
        PointValuePair solution;
        if (isMaximizingPlayer) {
            solution = new SimplexSolver().optimize(
                f,
                new LinearConstraintSet(constraints),
                GoalType.MAXIMIZE,
                nonNegative);
        } else {
            solution = new SimplexSolver().optimize(
                f,
                new LinearConstraintSet(constraints),
                GoalType.MINIMIZE,
                nonNegative);
        }
        return solution.getPoint();
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