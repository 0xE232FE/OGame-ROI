package org.quark.ogame;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OGameRules {
	private static abstract class ImprovementScheme {
		private final double theInitialMetalCost;
		private final double theInitialCrystalCost;
		private final double theInitialDeutCost;
		private final double theLevelExponent;
		private final boolean isResearch;

		public ImprovementScheme(double initialMetalCost, double initialCrystalCost, double initialDeutCost, double levelExponent,
				boolean research) {
			theInitialMetalCost = initialMetalCost;
			theInitialCrystalCost = initialCrystalCost;
			theInitialDeutCost = initialDeutCost;
			theLevelExponent = levelExponent;
			isResearch = research;
		}

		OGameCost getUpgradeCost(OGameState initState, int preLevel, int postLevel) {
			long[] cost = new long[3];
			if (postLevel == 0) {
				return OGameCost.ZERO;
			}
			// POW(exp, preLevel)*((1-POW(exp, ABS(postLevel-preLevel)))/(1-exp))
			double levelUp = Math.pow(theLevelExponent, preLevel)
					* ((1 - Math.pow(theLevelExponent, postLevel - preLevel)) / (1 - theLevelExponent));
			cost[0] = (long) Math.ceil(theInitialMetalCost * levelUp);
			cost[1] = (long) Math.ceil(theInitialCrystalCost * levelUp);
			cost[2] = (long) Math.ceil(theInitialDeutCost * levelUp);
			double upgradeHours;
			if (isResearch) {
				upgradeHours = (cost[0] + cost[1]) / 1000.0 / initState.getUniSpeed()
						/ (1 + initState.getBuildingLevel(OGameBuildingType.ResearchLab) * (1 + initState.getIRN()));
			} else {
				upgradeHours = (cost[0] + cost[1]) / 2500.0 / initState.getUniSpeed() / 11
						/ pow2(initState.getBuildingLevel(OGameBuildingType.Nanite));
			}
			Duration upgradeTime = Duration.ofSeconds(Math.round(upgradeHours * 3600));
			return new OGameCost(isResearch ? null : cost, isResearch ? cost : null, //
					isResearch ? null : upgradeTime, isResearch ? upgradeTime : null);
		}
	}

	private static class BuildingImprovementScheme extends ImprovementScheme {
		BuildingImprovementScheme(double initialMetalCost, double initialCrystalCost, double initialDeutCost, double levelExponent) {
			super(initialMetalCost, initialCrystalCost, initialDeutCost, levelExponent, false);
		}

		@Override
		OGameCost getUpgradeCost(OGameState initState, int preLevel, int postLevel) {
			return super.getUpgradeCost(initState, preLevel, postLevel).multiply(initState.getPlanets());
		}
	}

	private static class TechImprovementScheme extends ImprovementScheme {
		TechImprovementScheme(double initialMetalCost, double initialCrystalCost, double initialDeutCost, double levelExponent) {
			super(initialMetalCost, initialCrystalCost, initialDeutCost, levelExponent, true);
		}
	}

	private static class ColonyImprovementScheme extends TechImprovementScheme {
		ColonyImprovementScheme(double initialMetalCost, double initialCrystalCost, double initialDeutCost, double levelExponent) {
			super(initialMetalCost, initialCrystalCost, initialDeutCost, levelExponent);
		}

		@Override
		OGameCost getUpgradeCost(OGameState initState, int preLevel, int postLevel) {
			return super.getUpgradeCost(initState, preLevel * 2 - 3, postLevel * 2 - 3)//
					.plus(initState.getAccountValue().justBuildings().divide(initState.getPlanets()));
		}
	}

	private interface ProductionScheme {
		void addProduction(OGameState state, double[] production, double energyFactor); // Metal/crystal/deut production

		double getEnergyConsumption(OGameState state);
	}

	private static class MineProductionScheme implements ProductionScheme {
		private final OGameBuildingType theResourceType;
		private final double theBaseProduction;
		private final double theProductionFactor;
		private final double thePlasmaBonus;
		private final double theTempBonusOffset;
		private final double theTempBonusMult;
		private final int theEnergyMult;

		MineProductionScheme(OGameBuildingType resourceType, double baseProduction, double productionFactor, double plasmaBonus,
				double tempBonusOffset, double tempBonusMult, int energyMult) {
			theResourceType = resourceType;
			theBaseProduction = baseProduction;
			theProductionFactor = productionFactor;
			thePlasmaBonus = plasmaBonus;
			theTempBonusOffset = tempBonusOffset;
			theTempBonusMult = tempBonusMult;
			theEnergyMult = energyMult;
		}

		@Override
		public void addProduction(OGameState state, double[] production, double energyFactor) {
			int mineLevel = state.getBuildingLevel(theResourceType);
			double p = theBaseProduction + theProductionFactor * mineLevel * Math.pow(1.1, mineLevel);
			p *= theTempBonusOffset - theTempBonusMult * state.getAvgPlanetTemp();
			p *= (1 + thePlasmaBonus / 100 * state.getPlasmaTech());// Plasma adjustment
			p *= state.getUtilization(theResourceType.ordinal());
			p *= Math.min(1, energyFactor);
			p *= state.getUniSpeed() * state.getPlanets();
			production[theResourceType.ordinal()] += p;
		}

		@Override
		public double getEnergyConsumption(OGameState state) {
			int mineLevel = state.getBuildingLevel(theResourceType);
			return -theEnergyMult * mineLevel * Math.pow(1.1, mineLevel) * state.getUtilization(theResourceType.ordinal());
		}
	}

	private static class FusionProductionScheme implements ProductionScheme {
		@Override
		public void addProduction(OGameState state, double[] production, double energyFactor) {
			production[2] -= 10 * state.getUniSpeed() * state.getBuildingLevel(OGameBuildingType.Fusion)
					* Math.pow(1.1, state.getBuildingLevel(OGameBuildingType.Fusion))
					* state.getUtilization(OGameBuildingType.Fusion.ordinal());
		}

		@Override
		public double getEnergyConsumption(OGameState state) {
			return 30 * state.getBuildingLevel(OGameBuildingType.Fusion) * state.getUtilization(OGameBuildingType.Fusion.ordinal())
					* Math.pow(1.05 + (0.01 * state.getEnergyTech()), state.getBuildingLevel(OGameBuildingType.Fusion));
		}
	}

	private final Map<OGameImprovementType, ImprovementScheme> theImprovementSchemes;
	private final List<ProductionScheme> theProductionSchemes;

	public OGameRules() {
		theImprovementSchemes = new HashMap<>();
		theImprovementSchemes.put(OGameImprovementType.Metal, new BuildingImprovementScheme(60, 15, 0, 1.5));
		theImprovementSchemes.put(OGameImprovementType.Crystal, new BuildingImprovementScheme(48, 24, 0, 1.6));
		theImprovementSchemes.put(OGameImprovementType.Deut, new BuildingImprovementScheme(225, 75, 0, 1.5));
		theImprovementSchemes.put(OGameImprovementType.Fusion, new BuildingImprovementScheme(900, 360, 180, 1.8));
		theImprovementSchemes.put(OGameImprovementType.Energy, new TechImprovementScheme(0, 800, 400, 2));
		theImprovementSchemes.put(OGameImprovementType.Plasma, new TechImprovementScheme(2000, 4000, 1000, 2));
		theImprovementSchemes.put(OGameImprovementType.Planet, new ColonyImprovementScheme(4000, 8000, 4000, 1.75));
		theImprovementSchemes.put(OGameImprovementType.Nanite, new BuildingImprovementScheme(1000000, 500000, 100000, 2));
		theImprovementSchemes.put(OGameImprovementType.IRN, new TechImprovementScheme(240000, 400000, 160000, 2));
		theImprovementSchemes.put(OGameImprovementType.ResearchLab, new BuildingImprovementScheme(200, 400, 200, 2));

		theProductionSchemes = new ArrayList<>();
		theProductionSchemes.add(new MineProductionScheme(OGameBuildingType.Metal, 30, 30, 1, 1, 0, 10));
		theProductionSchemes.add(new MineProductionScheme(OGameBuildingType.Crystal, 15, 20, .66, 1, 0, 10));
		theProductionSchemes.add(new MineProductionScheme(OGameBuildingType.Deuterium, 0, 10, .33, 1.36, 0.004, 20));
		theProductionSchemes.add(new FusionProductionScheme());
	}

	public OGameCost getUpgradeCost(OGameState initState, OGameImprovementType improvement, int preLevel, int postLevel) {
		return theImprovementSchemes.get(improvement).getUpgradeCost(initState, preLevel, postLevel);
	}

	public double[] getEnergyProductionConsumption(OGameState state) {
		double production = state.getSatelliteEnergy(), consumption = 0;
		for (ProductionScheme scheme : theProductionSchemes) {
			double energy = scheme.getEnergyConsumption(state);
			if (energy > 0) {
				production += energy;
			} else {
				consumption -= energy;
			}
		}
		return new double[] { production, consumption };
	}

	public double[] getResourceProduction(OGameState state) {
		double[] energy = getEnergyProductionConsumption(state);
		double energyFactor = energy[0] == 0 ? 0 : energy[0] / energy[1];
		double[] production = new double[3];
		for (ProductionScheme scheme : theProductionSchemes) {
			scheme.addProduction(state, production, energyFactor);
		}
		return production;
	}

	private static final int[] POW_2 = new int[100];

	static {
		int pow2 = 1;
		for (int i = 0; i < POW_2.length; i++) {
			POW_2[i] = pow2;
			pow2 *= 2;
		}
	}

	private static int pow2(int num) {
		return POW_2[num];
	}
}
